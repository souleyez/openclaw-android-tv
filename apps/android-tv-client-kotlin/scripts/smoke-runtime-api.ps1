param(
    [string]$BaseUrl = $env:OPENCLAW_API_BASE_URL,
    [string]$SessionToken = $env:OPENCLAW_SESSION_TOKEN,
    [string]$ResourceSessionId = $env:OPENCLAW_RESOURCE_SESSION_ID,
    [string]$ResourceSessionRequestAppId = $env:OPENCLAW_RESOURCE_SESSION_REQUEST_APP_ID,
    [string]$ResourceSessionRequestProviderScope = $env:OPENCLAW_RESOURCE_SESSION_REQUEST_PROVIDER_SCOPE,
    [string]$ResourceSessionRequestLeaseProfile = $env:OPENCLAW_RESOURCE_SESSION_REQUEST_LEASE_PROFILE,
    [string]$BootstrapPrincipalType = $env:OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE,
    [string]$BootstrapPrincipalKey = $env:OPENCLAW_BOOTSTRAP_PRINCIPAL_KEY,
    [string]$BootstrapPrincipalLabel = $env:OPENCLAW_BOOTSTRAP_PRINCIPAL_LABEL,
    [string]$BootstrapProjectKey = $env:OPENCLAW_PROJECT_KEY,
    [string]$BootstrapDeviceFingerprint = $env:OPENCLAW_DEVICE_FINGERPRINT,
    [string]$BootstrapDeviceName = $env:OPENCLAW_DEVICE_NAME,
    [string]$BootstrapClientVersion = $env:OPENCLAW_CLIENT_VERSION,
    [string]$BootstrapOsFamily = $env:OPENCLAW_OS_FAMILY,
    [int]$TimeoutSeconds = 30,
    [string]$OutputDir,
    [switch]$ExerciseResourceSessionLifecycle,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = "https://oc.goods-editor.com/api"
}

if ([string]::IsNullOrWhiteSpace($BootstrapPrincipalType)) {
    $BootstrapPrincipalType = "device"
}
if ([string]::IsNullOrWhiteSpace($BootstrapProjectKey)) {
    $BootstrapProjectKey = "openclaw-android-tv"
}
if ([string]::IsNullOrWhiteSpace($BootstrapClientVersion)) {
    $BootstrapClientVersion = "0.1.0"
}
if ([string]::IsNullOrWhiteSpace($BootstrapOsFamily)) {
    $BootstrapOsFamily = "android-tv"
}

$BaseUrl = $BaseUrl.Trim().TrimEnd("/")
$ResourceSessionId = if ([string]::IsNullOrWhiteSpace($ResourceSessionId)) { $null } else { $ResourceSessionId.Trim() }
$ResourceSessionRequestAppId = if ([string]::IsNullOrWhiteSpace($ResourceSessionRequestAppId)) { $null } else { $ResourceSessionRequestAppId.Trim() }
$ResourceSessionRequestProviderScope = if ([string]::IsNullOrWhiteSpace($ResourceSessionRequestProviderScope)) { $null } else { $ResourceSessionRequestProviderScope.Trim() }
$ResourceSessionRequestLeaseProfile = if ([string]::IsNullOrWhiteSpace($ResourceSessionRequestLeaseProfile)) { $null } else { $ResourceSessionRequestLeaseProfile.Trim() }
if ($TimeoutSeconds -le 0) {
    throw "TimeoutSeconds must be greater than 0."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "artifacts\runtime-smoke"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Resolve-EndpointMode {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    $uri = [System.Uri]::new(($Url.TrimEnd("/") + "/"))
    $path = $uri.AbsolutePath.TrimEnd("/")
    if ([string]::IsNullOrWhiteSpace($path)) {
        $path = "/"
    }

    $mode = if ($uri.Host -eq "oc.goods-editor.com" -and $path -eq "/api") {
        "canonical"
    } else {
        "custom"
    }

    return [ordered]@{
        rawBaseUrl        = $Url
        normalizedBaseUrl = $Url
        host              = $uri.Host
        path              = $path
        mode              = $mode
    }
}

function Build-RequestUrl {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedBaseUrl,
        [Parameter(Mandatory = $true)]
        [string]$RelativePath,
        [hashtable]$Query = @{}
    )

    $builder = [System.UriBuilder]::new("$($ResolvedBaseUrl.TrimEnd('/'))/$($RelativePath.TrimStart('/'))")
    if ($Query.Count -gt 0) {
        $pairs = foreach ($name in ($Query.Keys | Sort-Object)) {
            $value = [string]$Query[$name]
            "{0}={1}" -f [System.Uri]::EscapeDataString([string]$name), [System.Uri]::EscapeDataString($value)
        }
        $builder.Query = ($pairs -join "&")
    }
    return $builder.Uri.AbsoluteUri
}

function Get-ObjectValue {
    param(
        [Parameter(Mandatory = $true)]
        $InputObject,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $current = $InputObject
    foreach ($segment in ($Path -split "\.")) {
        if ($null -eq $current) {
            return $null
        }
        $property = $current.PSObject.Properties[$segment]
        if ($null -eq $property) {
            return $null
        }
        $current = $property.Value
    }
    return $current
}

function Assert-FieldPresent {
    param(
        [Parameter(Mandatory = $true)]
        $InputObject,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $value = Get-ObjectValue -InputObject $InputObject -Path $Path
    if ($null -eq $value) {
        throw "Response field '$Path' is missing."
    }
    if ($value -is [string] -and [string]::IsNullOrWhiteSpace($value)) {
        throw "Response field '$Path' is blank."
    }
    return $value
}

function Save-SmokeArtifact {
    param(
        [Parameter(Mandatory = $true)]
        $Payload,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $Payload | ConvertTo-Json -Depth 10 | Set-Content -Path $Path -Encoding UTF8
}

function Select-DefinedFields {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload
    )

    $merged = [ordered]@{}
    foreach ($entry in $Payload.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            continue
        }
        if ($entry.Value -is [string] -and [string]::IsNullOrWhiteSpace($entry.Value)) {
            continue
        }
        $merged[$entry.Key] = $entry.Value
    }
    return $merged
}

function Get-ResourceSessionValidation {
    param(
        [Parameter(Mandatory = $true)]
        $InputObject,
        [bool]$RequireResourceSessionId = $false
    )

    $resourceSessionId = if ($RequireResourceSessionId) {
        Assert-FieldPresent -InputObject $InputObject -Path "resourceSessionId"
    } else {
        Get-ObjectValue -InputObject $InputObject -Path "resourceSessionId"
    }
    $entitlementSummary = Get-ObjectValue -InputObject $InputObject -Path "entitlementSummary"
    $appAccountLease = Get-ObjectValue -InputObject $InputObject -Path "appAccountLease"
    $modelLease = Get-ObjectValue -InputObject $InputObject -Path "modelLease"

    return [ordered]@{
        resourceSessionId = $resourceSessionId
        queueStatus       = Assert-FieldPresent -InputObject $InputObject -Path "queueStatus"
        priorityClass     = Assert-FieldPresent -InputObject $InputObject -Path "priorityClass"
        queuePosition     = Get-ObjectValue -InputObject $InputObject -Path "queuePosition"
        estimatedWaitSeconds = Get-ObjectValue -InputObject $InputObject -Path "estimatedWaitSeconds"
        paymentState      = if ($null -ne $entitlementSummary) {
            Get-ObjectValue -InputObject $InputObject -Path "entitlementSummary.paymentState"
        } else {
            $null
        }
        hasAppAccountLease = $null -ne $appAccountLease
        hasModelLease    = $null -ne $modelLease
        expiresAt        = Get-ObjectValue -InputObject $InputObject -Path "expiresAt"
        updatedAt        = Assert-FieldPresent -InputObject $InputObject -Path "updatedAt"
    }
}

function Invoke-JsonGet {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.Http.HttpClient]$Client,
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [Parameter(Mandatory = $true)]
        [bool]$RequiresAuth,
        [string]$BearerToken
    )

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $Url)
    if ($RequiresAuth) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $BearerToken)
    }

    try {
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        $bodyText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $body = $null
        if (-not [string]::IsNullOrWhiteSpace($bodyText)) {
            try {
                $body = $bodyText | ConvertFrom-Json -Depth 32
            } catch {
                throw "Response body is not valid JSON: $($_.Exception.Message)"
            }
        }
        return [ordered]@{
            statusCode = [int]$response.StatusCode
            body       = $body
            bodyText   = $bodyText
        }
    } finally {
        $request.Dispose()
    }
}

function Invoke-JsonPost {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.Http.HttpClient]$Client,
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload,
        [bool]$RequiresAuth = $false,
        [string]$BearerToken
    )

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $Url)
    if ($RequiresAuth) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $BearerToken)
    }

    $jsonBody = $Payload | ConvertTo-Json -Depth 10 -Compress
    $request.Content = [System.Net.Http.StringContent]::new(
        $jsonBody,
        [System.Text.Encoding]::UTF8,
        "application/json"
    )

    try {
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        $bodyText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $body = $null
        if (-not [string]::IsNullOrWhiteSpace($bodyText)) {
            try {
                $body = $bodyText | ConvertFrom-Json -Depth 32
            } catch {
                throw "Response body is not valid JSON: $($_.Exception.Message)"
            }
        }
        return [ordered]@{
            statusCode = [int]$response.StatusCode
            body       = $body
            bodyText   = $bodyText
        }
    } finally {
        $request.Dispose()
    }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$artifactPath = Join-Path $OutputDir "runtime-api-smoke-$timestamp.json"
$endpointSummary = Resolve-EndpointMode -Url $BaseUrl

$smokeSummary = [ordered]@{
    executedAt   = (Get-Date).ToString("o")
    dryRun       = [bool]$DryRun
    endpoint     = $endpointSummary
    artifactPath = $artifactPath
    auth         = [ordered]@{
        sessionTokenSource = if ([string]::IsNullOrWhiteSpace($SessionToken)) { "bootstrap_or_missing" } else { "env_session_token" }
        bootstrapEnabled   = (-not [string]::IsNullOrWhiteSpace($BootstrapPrincipalKey)) -and (-not [string]::IsNullOrWhiteSpace($BootstrapDeviceFingerprint))
    }
    resourceSessionLifecycle = [ordered]@{
        enabled               = [bool]$ExerciseResourceSessionLifecycle
        requestAppId          = $ResourceSessionRequestAppId
        requestProviderScope  = $ResourceSessionRequestProviderScope
        requestLeaseProfile   = $ResourceSessionRequestLeaseProfile
        statusTarget          = if ($ExerciseResourceSessionLifecycle) { "request_response" } elseif ([string]::IsNullOrWhiteSpace($ResourceSessionId)) { "server_default" } else { "explicit_query" }
        explicitStatusSession = $ResourceSessionId
    }
    requests     = @()
    success      = $false
}

$readOnlyRequests = @(
    [ordered]@{
        name         = "tv-home-config"
        relativePath = "me/tv-home-config"
        requiresAuth = $false
        query        = @{}
        validate     = {
            param($Body)
            [ordered]@{
                projectKey              = Assert-FieldPresent -InputObject $Body -Path "projectKey"
                runtimeManifestPath     = Assert-FieldPresent -InputObject $Body -Path "runtimeManifestPath"
                resourceSessionBasePath = Assert-FieldPresent -InputObject $Body -Path "resourceSessionBasePath"
                manifestPollAfterSeconds = Get-ObjectValue -InputObject $Body -Path "manifestPollAfterSeconds"
            }
        }
    },
    [ordered]@{
        name         = "runtime-manifest"
        relativePath = "me/runtime-manifest"
        requiresAuth = $true
        query        = @{}
        validate     = {
            param($Body)
            $apps = Get-ObjectValue -InputObject $Body -Path "apps"
            $adSlots = Get-ObjectValue -InputObject $Body -Path "adSlots"
            [ordered]@{
                manifestVersion   = Assert-FieldPresent -InputObject $Body -Path "manifestVersion"
                pollAfterSeconds  = Get-ObjectValue -InputObject $Body -Path "pollAfterSeconds"
                appCount          = if ($apps -is [System.Collections.IEnumerable]) { @($apps).Count } else { 0 }
                adSlotCount       = if ($adSlots -is [System.Collections.IEnumerable]) { @($adSlots).Count } else { 0 }
            }
        }
    },
    [ordered]@{
        name         = "entitlement"
        relativePath = "me/entitlement"
        requiresAuth = $true
        query        = @{}
        validate     = {
            param($Body)
            [ordered]@{
                accountId     = Assert-FieldPresent -InputObject $Body -Path "accountId"
                paymentState  = Assert-FieldPresent -InputObject $Body -Path "paymentState"
                priorityClass = Assert-FieldPresent -InputObject $Body -Path "priorityClass"
                renewalState  = Assert-FieldPresent -InputObject $Body -Path "renewalState"
            }
        }
    }
)

$defaultStatusRequest = [ordered]@{
    name         = "resource-session-status"
    relativePath = "client/resource-session/status"
    requiresAuth = $true
    query        = if ([string]::IsNullOrWhiteSpace($ResourceSessionId)) { @{} } else { @{ resourceSessionId = $ResourceSessionId } }
    validate     = {
        param($Body)
        Get-ResourceSessionValidation -InputObject $Body
    }
}

$resourceSessionRequestPayload = Select-DefinedFields -Payload ([ordered]@{
    appId         = $ResourceSessionRequestAppId
    providerScope = $ResourceSessionRequestProviderScope
    leaseProfile  = $ResourceSessionRequestLeaseProfile
})

Write-Host ("Runtime API smoke mode: {0} ({1})" -f $endpointSummary.mode, $endpointSummary.normalizedBaseUrl)
Write-Host ("Artifact path: {0}" -f $artifactPath)
if ($ExerciseResourceSessionLifecycle) {
    Write-Host "Resource-session lifecycle smoke enabled: request -> status -> release"
    if (-not [string]::IsNullOrWhiteSpace($ResourceSessionId)) {
        Write-Host "Existing ResourceSessionId input will be ignored for lifecycle status checks."
    }
}

if ($DryRun) {
    $authPreview = if (-not [string]::IsNullOrWhiteSpace($SessionToken)) {
        "Bearer <redacted>"
    } elseif ($smokeSummary.auth.bootstrapEnabled) {
        "<bootstrap-session>"
    } else {
        "<required>"
    }
    if ([string]::IsNullOrWhiteSpace($SessionToken) -and
        (-not [string]::IsNullOrWhiteSpace($BootstrapPrincipalKey)) -and
        (-not [string]::IsNullOrWhiteSpace($BootstrapDeviceFingerprint))
    ) {
        $bootstrapUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/bootstrap/auth"
        $smokeSummary.requests += [ordered]@{
            name         = "bootstrap-auth"
            method       = "POST"
            url          = $bootstrapUrl
            requiresAuth = $false
            authorization = "<none>"
            dryRun       = $true
            statusCode   = $null
            validation   = [ordered]@{
                principalType     = $BootstrapPrincipalType
                projectKey        = $BootstrapProjectKey
                deviceFingerprint = $BootstrapDeviceFingerprint
            }
        }
    }
    foreach ($request in $readOnlyRequests) {
        $url = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath $request.relativePath -Query $request.query
        $smokeSummary.requests += [ordered]@{
            name            = $request.name
            method          = "GET"
            url             = $url
            requiresAuth    = [bool]$request.requiresAuth
            authorization   = if ($request.requiresAuth) {
                $authPreview
            } else {
                "<none>"
            }
            dryRun          = $true
            statusCode      = $null
            validation      = $null
        }
    }
    if ($ExerciseResourceSessionLifecycle) {
        $plannedResourceSessionId = "requested-resource-session-id"
        $requestUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/resource-session/request"
        $statusUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/resource-session/status" -Query @{ resourceSessionId = $plannedResourceSessionId }
        $releaseUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/resource-session/release"

        $smokeSummary.requests += [ordered]@{
            name          = "resource-session-request"
            method        = "POST"
            url           = $requestUrl
            requiresAuth  = $true
            authorization = $authPreview
            dryRun        = $true
            payload       = $resourceSessionRequestPayload
            statusCode    = $null
            validation    = [ordered]@{
                expectedResourceSessionId = $plannedResourceSessionId
            }
        }
        $smokeSummary.requests += [ordered]@{
            name          = "resource-session-status"
            method        = "GET"
            url           = $statusUrl
            requiresAuth  = $true
            authorization = $authPreview
            dryRun        = $true
            statusCode    = $null
            validation    = [ordered]@{
                resourceSessionId = $plannedResourceSessionId
            }
        }
        $smokeSummary.requests += [ordered]@{
            name          = "resource-session-release"
            method        = "POST"
            url           = $releaseUrl
            requiresAuth  = $true
            authorization = $authPreview
            dryRun        = $true
            payload       = [ordered]@{
                resourceSessionId = $plannedResourceSessionId
            }
            statusCode    = $null
            validation    = [ordered]@{
                resourceSessionId = $plannedResourceSessionId
            }
        }
    } else {
        $url = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath $defaultStatusRequest.relativePath -Query $defaultStatusRequest.query
        $smokeSummary.requests += [ordered]@{
            name          = $defaultStatusRequest.name
            method        = "GET"
            url           = $url
            requiresAuth  = [bool]$defaultStatusRequest.requiresAuth
            authorization = $authPreview
            dryRun        = $true
            statusCode    = $null
            validation    = $null
        }
    }
    $smokeSummary.success = $true
    Save-SmokeArtifact -Payload $smokeSummary -Path $artifactPath
    Write-Host "Dry run complete."
    return
}

if ([string]::IsNullOrWhiteSpace($SessionToken) -and (
        [string]::IsNullOrWhiteSpace($BootstrapPrincipalKey) -or [string]::IsNullOrWhiteSpace($BootstrapDeviceFingerprint)
    )
) {
    throw "Provide OPENCLAW_SESSION_TOKEN, or provide OPENCLAW_BOOTSTRAP_PRINCIPAL_KEY plus OPENCLAW_DEVICE_FINGERPRINT to bootstrap a session."
}

$handler = [System.Net.Http.HttpClientHandler]::new()
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)

$executionException = $null
$lifecycleResourceSessionId = $null
$lifecycleReleaseSucceeded = $false

try {
    $effectiveSessionToken = $SessionToken

    if ([string]::IsNullOrWhiteSpace($effectiveSessionToken)) {
        $bootstrapUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/bootstrap/auth"
        $bootstrapPayload = [ordered]@{
            principalType     = $BootstrapPrincipalType
            principalKey      = $BootstrapPrincipalKey
            principalLabel    = $BootstrapPrincipalLabel
            projectKey        = $BootstrapProjectKey
            deviceFingerprint = $BootstrapDeviceFingerprint
            deviceName        = $BootstrapDeviceName
            osFamily          = $BootstrapOsFamily
            clientVersion     = $BootstrapClientVersion
        }
        $mergedPayload = Select-DefinedFields -Payload $bootstrapPayload

        Write-Host ("POST {0}" -f $bootstrapUrl)
        try {
            $bootstrapResult = Invoke-JsonPost -Client $client -Url $bootstrapUrl -Payload $mergedPayload
            if ($bootstrapResult.statusCode -lt 200 -or $bootstrapResult.statusCode -ge 300) {
                throw "HTTP $($bootstrapResult.statusCode): $($bootstrapResult.bodyText)"
            }
            $effectiveSessionToken = Assert-FieldPresent -InputObject $bootstrapResult.body -Path "session.token"
            $smokeSummary.auth.sessionTokenSource = "bootstrap_auth"
            $smokeSummary.requests += [ordered]@{
                name         = "bootstrap-auth"
                method       = "POST"
                url          = $bootstrapUrl
                requiresAuth = $false
                statusCode   = $bootstrapResult.statusCode
                validation   = [ordered]@{
                    userId           = Assert-FieldPresent -InputObject $bootstrapResult.body -Path "user.id"
                    deviceId         = Assert-FieldPresent -InputObject $bootstrapResult.body -Path "device.id"
                    sessionExpiresAt = Assert-FieldPresent -InputObject $bootstrapResult.body -Path "session.expiresAt"
                }
            }
            Write-Host ("OK  bootstrap-auth -> HTTP {0}" -f $bootstrapResult.statusCode)
        } catch {
            $smokeSummary.requests += [ordered]@{
                name         = "bootstrap-auth"
                method       = "POST"
                url          = $bootstrapUrl
                requiresAuth = $false
                statusCode   = $null
                error        = $_.Exception.Message
            }
            throw
        }
    }

    foreach ($request in $readOnlyRequests) {
        $url = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath $request.relativePath -Query $request.query
        Write-Host ("GET {0}" -f $url)

        try {
            $result = Invoke-JsonGet -Client $client -Url $url -RequiresAuth ([bool]$request.requiresAuth) -BearerToken $effectiveSessionToken
            if ($result.statusCode -lt 200 -or $result.statusCode -ge 300) {
                throw "HTTP $($result.statusCode): $($result.bodyText)"
            }

            $validation = & $request.validate $result.body
            $smokeSummary.requests += [ordered]@{
                name         = $request.name
                method       = "GET"
                url          = $url
                requiresAuth = [bool]$request.requiresAuth
                statusCode   = $result.statusCode
                validation   = $validation
            }
            Write-Host ("OK  {0} -> HTTP {1}" -f $request.name, $result.statusCode)
        } catch {
            $smokeSummary.requests += [ordered]@{
                name         = $request.name
                method       = "GET"
                url          = $url
                requiresAuth = [bool]$request.requiresAuth
                statusCode   = $null
                error        = $_.Exception.Message
            }
            throw
        }
    }

    if ($ExerciseResourceSessionLifecycle) {
        $requestUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/resource-session/request"
        Write-Host ("POST {0}" -f $requestUrl)
        try {
            $requestResult = Invoke-JsonPost -Client $client -Url $requestUrl -Payload $resourceSessionRequestPayload -RequiresAuth $true -BearerToken $effectiveSessionToken
            if ($requestResult.statusCode -lt 200 -or $requestResult.statusCode -ge 300) {
                throw "HTTP $($requestResult.statusCode): $($requestResult.bodyText)"
            }
            $validation = Get-ResourceSessionValidation -InputObject $requestResult.body -RequireResourceSessionId $true
            $lifecycleResourceSessionId = $validation.resourceSessionId
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-request"
                method       = "POST"
                url          = $requestUrl
                requiresAuth = $true
                payload      = $resourceSessionRequestPayload
                statusCode   = $requestResult.statusCode
                validation   = $validation
            }
            Write-Host ("OK  resource-session-request -> HTTP {0}" -f $requestResult.statusCode)
        } catch {
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-request"
                method       = "POST"
                url          = $requestUrl
                requiresAuth = $true
                payload      = $resourceSessionRequestPayload
                statusCode   = $null
                error        = $_.Exception.Message
            }
            throw
        }

        $statusUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/resource-session/status" -Query @{ resourceSessionId = $lifecycleResourceSessionId }
        Write-Host ("GET {0}" -f $statusUrl)
        try {
            $statusResult = Invoke-JsonGet -Client $client -Url $statusUrl -RequiresAuth $true -BearerToken $effectiveSessionToken
            if ($statusResult.statusCode -lt 200 -or $statusResult.statusCode -ge 300) {
                throw "HTTP $($statusResult.statusCode): $($statusResult.bodyText)"
            }
            $validation = Get-ResourceSessionValidation -InputObject $statusResult.body -RequireResourceSessionId $true
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-status"
                method       = "GET"
                url          = $statusUrl
                requiresAuth = $true
                statusCode   = $statusResult.statusCode
                validation   = $validation
            }
            Write-Host ("OK  resource-session-status -> HTTP {0}" -f $statusResult.statusCode)
        } catch {
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-status"
                method       = "GET"
                url          = $statusUrl
                requiresAuth = $true
                statusCode   = $null
                error        = $_.Exception.Message
            }
            throw
        }

        $releaseUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/resource-session/release"
        $releasePayload = [ordered]@{
            resourceSessionId = $lifecycleResourceSessionId
        }
        Write-Host ("POST {0}" -f $releaseUrl)
        try {
            $releaseResult = Invoke-JsonPost -Client $client -Url $releaseUrl -Payload $releasePayload -RequiresAuth $true -BearerToken $effectiveSessionToken
            if ($releaseResult.statusCode -lt 200 -or $releaseResult.statusCode -ge 300) {
                throw "HTTP $($releaseResult.statusCode): $($releaseResult.bodyText)"
            }
            $validation = Get-ResourceSessionValidation -InputObject $releaseResult.body -RequireResourceSessionId $true
            $lifecycleReleaseSucceeded = $true
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-release"
                method       = "POST"
                url          = $releaseUrl
                requiresAuth = $true
                payload      = $releasePayload
                statusCode   = $releaseResult.statusCode
                validation   = $validation
            }
            Write-Host ("OK  resource-session-release -> HTTP {0}" -f $releaseResult.statusCode)
        } catch {
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-release"
                method       = "POST"
                url          = $releaseUrl
                requiresAuth = $true
                payload      = $releasePayload
                statusCode   = $null
                error        = $_.Exception.Message
            }
            throw
        }
    } else {
        $statusUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath $defaultStatusRequest.relativePath -Query $defaultStatusRequest.query
        Write-Host ("GET {0}" -f $statusUrl)
        try {
            $statusResult = Invoke-JsonGet -Client $client -Url $statusUrl -RequiresAuth $true -BearerToken $effectiveSessionToken
            if ($statusResult.statusCode -lt 200 -or $statusResult.statusCode -ge 300) {
                throw "HTTP $($statusResult.statusCode): $($statusResult.bodyText)"
            }
            $validation = & $defaultStatusRequest.validate $statusResult.body
            $smokeSummary.requests += [ordered]@{
                name         = $defaultStatusRequest.name
                method       = "GET"
                url          = $statusUrl
                requiresAuth = $true
                statusCode   = $statusResult.statusCode
                validation   = $validation
            }
            Write-Host ("OK  {0} -> HTTP {1}" -f $defaultStatusRequest.name, $statusResult.statusCode)
        } catch {
            $smokeSummary.requests += [ordered]@{
                name         = $defaultStatusRequest.name
                method       = "GET"
                url          = $statusUrl
                requiresAuth = $true
                statusCode   = $null
                error        = $_.Exception.Message
            }
            throw
        }
    }

    $smokeSummary.success = $true
    Write-Host "Runtime API smoke completed successfully."
} catch {
    $executionException = $_.Exception
} finally {
    if ($ExerciseResourceSessionLifecycle -and -not [string]::IsNullOrWhiteSpace($lifecycleResourceSessionId) -and -not $lifecycleReleaseSucceeded) {
        $cleanupUrl = Build-RequestUrl -ResolvedBaseUrl $BaseUrl -RelativePath "client/resource-session/release"
        $cleanupPayload = [ordered]@{
            resourceSessionId = $lifecycleResourceSessionId
        }
        Write-Warning ("Best-effort cleanup release for resource session {0}" -f $lifecycleResourceSessionId)
        try {
            $cleanupResult = Invoke-JsonPost -Client $client -Url $cleanupUrl -Payload $cleanupPayload -RequiresAuth $true -BearerToken $effectiveSessionToken
            if ($cleanupResult.statusCode -lt 200 -or $cleanupResult.statusCode -ge 300) {
                throw "HTTP $($cleanupResult.statusCode): $($cleanupResult.bodyText)"
            }
            $validation = Get-ResourceSessionValidation -InputObject $cleanupResult.body -RequireResourceSessionId $true
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-release-cleanup"
                method       = "POST"
                url          = $cleanupUrl
                requiresAuth = $true
                payload      = $cleanupPayload
                statusCode   = $cleanupResult.statusCode
                validation   = $validation
            }
            Write-Host ("OK  resource-session-release-cleanup -> HTTP {0}" -f $cleanupResult.statusCode)
        } catch {
            $smokeSummary.requests += [ordered]@{
                name         = "resource-session-release-cleanup"
                method       = "POST"
                url          = $cleanupUrl
                requiresAuth = $true
                payload      = $cleanupPayload
                statusCode   = $null
                error        = $_.Exception.Message
            }
            Write-Warning ("Cleanup release failed: {0}" -f $_.Exception.Message)
        }
    }
    Save-SmokeArtifact -Payload $smokeSummary -Path $artifactPath
    $client.Dispose()
    $handler.Dispose()
}

if ($null -ne $executionException) {
    throw $executionException
}
