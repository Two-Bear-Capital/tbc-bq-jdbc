# Thread-scaling benchmark report

| Field | Value |
| --- | --- |
| Generated | 2026-07-27T22:25:51.620730Z |
| Driver version | 2.0.2 (68f6de1) |
| JVM | OpenJDK 64-Bit Server VM 25.0.3 |
| OS | Mac OS X aarch64 |
| Available processors | 10 |
| Mode | full |

| Benchmark | Threads | Throughput (ops/s) | Error (±) | Scaling | Efficiency |
| --- | ---: | ---: | ---: | ---: | ---: |
| getColumnsWarm | 1 | 6523.393 | 2775.990 | 1.00x | 100% |
| getColumnsWarm | 2 | 11801.550 | 4400.106 | 1.81x | 90% |
| getColumnsWarm | 4 | 18578.056 | 20479.676 | 2.85x | 71% |
| getColumnsWarm | 8 | 24218.185 | 24111.030 | 3.71x | 46% |
| getColumnsWarm | 16 | 26731.208 | 10474.726 | 4.10x | 26% |
| getTablesWarm | 1 | 125366.171 | 15887.564 | 1.00x | 100% |
| getTablesWarm | 2 | 217642.175 | 31717.614 | 1.74x | 87% |
| getTablesWarm | 4 | 337793.763 | 108578.089 | 2.69x | 67% |
| getTablesWarm | 8 | 437370.153 | 118224.765 | 3.49x | 44% |
| getTablesWarm | 16 | 474779.164 | 600034.215 | 3.79x | 24% |
| iterateResultSet | 1 | 0.263 | 0.118 | 1.00x | 100% |
| iterateResultSet | 2 | 0.598 | 0.432 | 2.28x | 114% |
| iterateResultSet | 4 | 1.317 | 0.919 | 5.02x | 125% |
| iterateResultSet | 8 | 2.779 | 1.623 | 10.58x | 132% |
| iterateResultSet | 16 | 5.800 | 2.329 | 22.09x | 138% |
| submitToFirstRow | 1 | 0.884 | 0.828 | 1.00x | 100% |
| submitToFirstRow | 2 | 2.125 | 1.259 | 2.40x | 120% |
| submitToFirstRow | 4 | 4.962 | 4.324 | 5.61x | 140% |
| submitToFirstRow | 8 | 10.445 | 3.812 | 11.81x | 148% |
| submitToFirstRow | 16 | 22.275 | 10.284 | 25.18x | 157% |

**Scaling** is throughput at N threads over throughput at 1 thread; **Efficiency** is that
divided by N. Efficiency well under 100% is normal — BigQuery, the network and the runner's
core count all cap it. The failure this watches for is a curve that *collapses*, where total
throughput stops rising with threads at all: that is the shape of #98.
