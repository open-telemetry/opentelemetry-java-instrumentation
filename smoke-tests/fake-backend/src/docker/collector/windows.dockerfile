FROM mcr.microsoft.com/windows/servercore:ltsc2019
COPY otelcontribcol_windows_amd64.exe /otelcontribcol_windows_amd64.exe
ENV NO_WINDOWS_SERVICE=1
ENTRYPOINT /otelcontribcol_windows_amd64.exe
