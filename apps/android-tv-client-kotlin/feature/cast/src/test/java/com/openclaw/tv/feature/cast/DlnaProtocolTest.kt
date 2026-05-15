package com.openclaw.tv.feature.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaProtocolTest {

    @Test
    fun device_description_exposes_media_renderer_services() {
        val xml = DlnaProtocol.buildDeviceDescriptionXml(config())

        assertTrue(xml.contains("<deviceType>${DlnaProtocol.MediaRendererDeviceType}</deviceType>"))
        assertTrue(xml.contains("<dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>"))
        assertTrue(xml.contains("<friendlyName>RS AITV Living Room</friendlyName>"))
        assertTrue(xml.contains("<modelDescription>OpenClaw TV DLNA Media Renderer</modelDescription>"))
        assertTrue(xml.contains("<UDN>uuid:00000000-0000-0000-0000-000000000001</UDN>"))
        assertTrue(xml.contains("<presentationURL>/</presentationURL>"))
        assertTrue(xml.contains("<controlURL>/dlna/avtransport/control</controlURL>"))
        assertTrue(xml.contains("<controlURL>/dlna/renderingcontrol/control</controlURL>"))
        assertTrue(xml.contains("<controlURL>/dlna/connectionmanager/control</controlURL>"))
    }

    @Test
    fun m_search_ssdp_all_generates_renderer_responses() {
        val responses = DlnaProtocol.buildSearchResponses(
            config = config(),
            location = "http://192.168.1.10:49152/dlna/description.xml",
            request = """
                M-SEARCH * HTTP/1.1
                HOST: 239.255.255.250:1900
                MAN: "ssdp:discover"
                ST: ssdp:all
                MX: 2
            """.trimIndent(),
        )

        assertFalse(responses.isEmpty())
        assertTrue(responses.any { it.contains("ST: ${DlnaProtocol.MediaRendererDeviceType}") })
        assertTrue(responses.any { it.contains("LOCATION: http://192.168.1.10:49152/dlna/description.xml") })
        assertTrue(responses.all { it.contains("USN: uuid:00000000-0000-0000-0000-000000000001") })
    }

    @Test
    fun m_search_unknown_target_is_ignored() {
        val responses = DlnaProtocol.buildSearchResponses(
            config = config(),
            location = "http://192.168.1.10:49152/dlna/description.xml",
            request = """
                M-SEARCH * HTTP/1.1
                MAN: "ssdp:discover"
                ST: urn:schemas-upnp-org:device:MediaServer:1
            """.trimIndent(),
        )

        assertEquals(emptyList<String>(), responses)
    }

    @Test
    fun m_search_specific_renderer_target_accepts_case_insensitive_headers() {
        val responses = DlnaProtocol.buildSearchResponses(
            config = config(),
            location = "http://192.168.1.10:49152/dlna/description.xml",
            request = """
                m-search * HTTP/1.1
                host: 239.255.255.250:1900
                man: "ssdp:discover"
                st: URN:SCHEMAS-UPNP-ORG:DEVICE:MEDIARENDERER:1
                mx: 1
            """.trimIndent(),
        )

        assertEquals(1, responses.size)
        assertTrue(responses.single().contains("ST: ${DlnaProtocol.MediaRendererDeviceType}"))
        assertTrue(responses.single().contains("USN: uuid:00000000-0000-0000-0000-000000000001::${DlnaProtocol.MediaRendererDeviceType}"))
    }

    @Test
    fun soap_helpers_extract_current_uri_and_action() {
        val body = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="${DlnaProtocol.AvTransportServiceType}">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>http://example.com/movie.mp4</CurrentURI>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        assertEquals(
            "SetAVTransportURI",
            DlnaProtocol.extractSoapAction("\"${DlnaProtocol.AvTransportServiceType}#SetAVTransportURI\""),
        )
        assertEquals("http://example.com/movie.mp4", DlnaProtocol.extractXmlTag(body, "CurrentURI"))
    }

    private fun config(): DlnaRendererConfig {
        return DlnaRendererConfig(
            uuid = "00000000-0000-0000-0000-000000000001",
            deviceName = "RS AITV Living Room",
        )
    }
}
