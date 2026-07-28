# Thread-scaling benchmark report

| Field | Value |
| --- | --- |
| Generated | 2026-07-27T23:56:17.806204Z |
| Driver version | 2.1.0 (cb609e7) |
| JVM | OpenJDK 64-Bit Server VM 25.0.3 |
| OS | Mac OS X aarch64 |
| Available processors | 10 |
| Mode | full |

| Benchmark | Threads | Throughput (ops/s) | Error (±) | Scaling | Efficiency |
| --- | ---: | ---: | ---: | ---: | ---: |
| getColumnsWarm | 1 | 651974.245 | 307391.908 | 1.00x | 100% |
| getColumnsWarm | 2 | 957529.228 | 3189648.152 | 1.47x | 73% |
| getColumnsWarm | 4 | 1589831.210 | 1067030.493 | 2.44x | 61% |
| getColumnsWarm | 8 | 1813643.580 | 1130313.604 | 2.78x | 35% |
| getColumnsWarm | 16 | 2031465.558 | 776774.681 | 3.12x | 19% |
| getTablesWarm | 1 | 2725667.468 | 104543.737 | 1.00x | 100% |
| getTablesWarm | 2 | 4608157.916 | 713371.304 | 1.69x | 85% |
| getTablesWarm | 4 | 7614654.892 | 1872224.227 | 2.79x | 70% |
| getTablesWarm | 8 | 10131809.695 | 5267244.188 | 3.72x | 46% |
| getTablesWarm | 16 | 11247262.953 | 6689509.079 | 4.13x | 26% |
| iterateResultSet | 1 | 0.297 | 0.115 | 1.00x | 100% |
| iterateResultSet | 2 | 0.647 | 0.461 | 2.18x | 109% |
| iterateResultSet | 4 | 1.434 | 1.038 | 4.83x | 121% |
| iterateResultSet | 8 | 2.884 | 1.989 | 9.70x | 121% |
| iterateResultSet | 16 | 6.287 | 2.174 | 21.15x | 132% |
| submitToFirstRow | 1 | 1.124 | 0.734 | 1.00x | 100% |
| submitToFirstRow | 2 | 2.504 | 2.518 | 2.23x | 111% |
| submitToFirstRow | 4 | 5.736 | 3.342 | 5.10x | 128% |
| submitToFirstRow | 8 | 11.895 | 3.636 | 10.58x | 132% |
| submitToFirstRow | 16 | 24.902 | 7.039 | 22.15x | 138% |

**Scaling** is throughput at N threads over throughput at 1 thread; **Efficiency** is that
divided by N. Efficiency well under 100% is normal — BigQuery, the network and the runner's
core count all cap it. The failure this watches for is a curve that *collapses*, where total
throughput stops rising with threads at all: that is the shape of #98.
