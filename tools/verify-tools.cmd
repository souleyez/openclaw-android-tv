@echo off
set "PATH=C:\Users\soulzyn\develop\node;C:\Users\soulzyn\develop\flutter\bin;C:\Users\soulzyn\develop\flutter\bin\mingit\cmd;%PATH%"
echo Node:
node -v
echo npm:
npm.cmd -v
echo Git:
git --version
echo Flutter:
flutter.bat --suppress-analytics --no-version-check --version
