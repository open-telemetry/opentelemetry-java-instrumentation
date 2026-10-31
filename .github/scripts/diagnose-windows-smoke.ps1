param(
  [Parameter(Mandatory)]
  [ValidateSet("parallel", "serial")]
  [string] $Mode,
  [Parameter(Mandatory)]
  [string] $OutputDirectory
)

$ErrorActionPreference = "Stop"
$env:DIAGNOSTIC_MODE = $Mode
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$initScript = Join-Path $OutputDirectory "diagnostic.init.gradle"
@'
gradle.projectsEvaluated {
  if (gradle.rootProject.name != "opentelemetry-java-instrumentation") {
    return
  }
  def smoke = gradle.rootProject.project(":smoke-tests")
  smoke.tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
    timeout.set(java.time.Duration.ofMinutes(20))
  }
  if (System.getenv("DIAGNOSTIC_MODE") == "serial") {
    smoke.tasks.named("testAdditionalVariants") {
      mustRunAfter(smoke.tasks.named("testLimitedMatrix"))
    }
  }
}
'@ | Set-Content -Path $initScript -Encoding utf8

$monitor = Start-Job -ArgumentList $OutputDirectory -ScriptBlock {
  param($OutputDirectory)
  $ErrorActionPreference = "Stop"

  function Capture($Executable, $Arguments, $File) {
    $process = Start-Process -FilePath $Executable -ArgumentList $Arguments `
      -RedirectStandardOutput "$File.txt" -RedirectStandardError "$File.stderr.txt" `
      -WindowStyle Hidden -PassThru
    try {
      if (-not $process.WaitForExit(20000)) {
        "Timed out after 20 seconds: $Executable $Arguments" |
          Set-Content "$File.timeout.txt"
      } else {
        $process.ExitCode | Set-Content "$File.exit.txt"
      }
    } finally {
      if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
      }
    }
  }

  while ($true) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $prefix = Join-Path $OutputDirectory $stamp
    try {
      @{
        timestamp = (Get-Date).ToUniversalTime().ToString("o")
        memory = Get-CimInstance Win32_OperatingSystem |
          Select-Object FreePhysicalMemory, TotalVisibleMemorySize, FreeVirtualMemory, TotalVirtualMemorySize
        pageFile = @(Get-CimInstance Win32_PageFileUsage |
          Select-Object Name, AllocatedBaseSize, CurrentUsage, PeakUsage)
        disks = @(Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" |
          Select-Object DeviceID, FreeSpace, Size)
        diskActivity = @(Get-CimInstance Win32_PerfFormattedData_PerfDisk_PhysicalDisk |
          Select-Object Name, CurrentDiskQueueLength, PercentDiskTime, DiskBytesPersec)
        processes = @(Get-Process |
          Where-Object ProcessName -In @("java", "dockerd", "vmcompute", "vmmem") |
          Select-Object Id, ProcessName, CPU, WorkingSet64, PrivateMemorySize64, Handles, Path)
      } | ConvertTo-Json -Depth 5 | Set-Content "$prefix-host.json" -Encoding utf8

      foreach ($javaProcess in Get-Process java -ErrorAction SilentlyContinue) {
        if ($javaProcess.Path) {
          $jcmd = Join-Path (Split-Path $javaProcess.Path) "jcmd.exe"
          if (Test-Path $jcmd) {
            Capture $jcmd @("$($javaProcess.Id)", "Thread.print", "-l") `
              "$prefix-java-$($javaProcess.Id)"
          }
        }
      }
      Capture "docker.exe" @("info") "$prefix-docker-info"
      Capture "docker.exe" @("ps", "--all", "--no-trunc") "$prefix-docker-ps"
      Capture "docker.exe" @("stats", "--no-stream") "$prefix-docker-stats"
      Capture "docker.exe" @(
        "events", "--since", "2m", "--until", (Get-Date).ToUniversalTime().ToString("o")
      ) "$prefix-docker-events"
      foreach ($daemon in Get-Process dockerd -ErrorAction SilentlyContinue) {
        $event = [System.Threading.EventWaitHandle]::OpenExisting("Global\stackdump-$($daemon.Id)")
        try {
          if (-not $event.Set()) {
            throw "Failed to request Docker daemon stack dump for PID $($daemon.Id)"
          }
        } finally {
          $event.Dispose()
        }
      }
      Start-Sleep -Seconds 1
      $stackFiles = @(Get-ChildItem "$env:ProgramData\docker" -Filter "goroutine-stacks-*.log" -File)
      if ($stackFiles.Count -eq 0) {
        throw "Docker daemon produced no stack dumps in $env:ProgramData\docker"
      }
      $stackFiles | Copy-Item -Destination $OutputDirectory
      Get-WinEvent -FilterHashtable @{
        LogName = "Application", "System"
        StartTime = (Get-Date).AddMinutes(-2)
        Level = 1, 2, 3, 4
      } -ErrorAction SilentlyContinue |
        Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message |
        ConvertTo-Json -Depth 3 |
        Set-Content "$prefix-events.json" -Encoding utf8
      "Captured diagnostics at $stamp"
    } catch {
      $_ | Out-String | Set-Content "$prefix-capture-error.txt"
      Write-Output "Diagnostic capture failed at ${stamp}: $_"
    }
    Start-Sleep -Seconds 120
  }
}

try {
  ./gradlew :smoke-tests:testLimitedMatrix :smoke-tests:testAdditionalVariants `
    -PsmokeTestSuite=payara --no-build-cache --init-script $initScript
  $result = $LASTEXITCODE
} finally {
  Stop-Job $monitor
  Receive-Job $monitor
  if ($monitor.ChildJobs[0].JobStateInfo.Reason) {
    Write-Warning $monitor.ChildJobs[0].JobStateInfo.Reason
  }
  Remove-Job $monitor
}
exit $result
