window.BENCHMARK_DATA = {
  "lastUpdate": 1680273646342,
  "repoUrl": "https://github.com/DataBiosphere/terra-workspace-data-service",
  "entries": {
    "Benchmark": [
      {
        "commit": {
          "author": {
            "name": "DataBiosphere",
            "username": "DataBiosphere"
          },
          "committer": {
            "name": "DataBiosphere",
            "username": "DataBiosphere"
          },
          "id": "56c4bd9a573c80208099108450f328a3f834a733",
          "message": "POC of JMH",
          "timestamp": "2023-03-21T17:47:19Z",
          "url": "https://github.com/DataBiosphere/terra-workspace-data-service/pull/196/commits/56c4bd9a573c80208099108450f328a3f834a733"
        },
        "date": 1680273645183,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.databiosphere.workspacedataservice.controller.ApiBenchmark.upsertRecord",
            "value": 380.6100763942394,
            "unit": "ops/s",
            "extra": "iterations: 2\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}