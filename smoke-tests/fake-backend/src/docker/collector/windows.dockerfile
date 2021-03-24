FROM mcr.microsoft.com/windows/servercore:ltsc2019
COPY otelcol_windows_amd64.exe /otelcol_windows_amd64.exe
ENV NO_WINDOWS_SERVICE=1
ENTRYPOINT /otelcol_windows_amd64.exe
