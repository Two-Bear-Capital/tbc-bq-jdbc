# Thread-scaling benchmark report

| Field | Value |
| --- | --- |
| Generated | 2026-07-27T23:13:11.495651Z |
| Driver version | 2.1.0 (180c29e) |
| JVM | OpenJDK 64-Bit Server VM 25.0.3 |
| OS | Mac OS X aarch64 |
| Available processors | 10 |
| Mode | full |

| Benchmark | Threads | Throughput (ops/s) | Error (±) | Scaling | Efficiency | Baseline (ops/s) | Δ |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| getColumnsWarm | 1 | 20582.019 | 8779.005 | 1.00x | 100% | 6523.393 | +215.5% |
| getColumnsWarm | 2 | 35195.816 | 15104.955 | 1.71x | 86% | 11801.550 | +198.2% |
| getColumnsWarm | 4 | 57514.445 | 56881.999 | 2.79x | 70% | 18578.056 | +209.6% |
| getColumnsWarm | 8 | 75984.192 | 63332.310 | 3.69x | 46% | 24218.185 | +213.7% |
| getColumnsWarm | 16 | 82740.996 | 87765.041 | 4.02x | 25% | 26731.208 | +209.5% |
| getTablesWarm | 1 | 437542.850 | 491977.160 | 1.00x | 100% | 125366.171 | +249.0% |
| getTablesWarm | 2 | 772654.549 | 660314.388 | 1.77x | 88% | 217642.175 | +255.0% |
| getTablesWarm | 4 | 1312870.207 | 241519.186 | 3.00x | 75% | 337793.763 | +288.7% |
| getTablesWarm | 8 | 1709393.674 | 649613.990 | 3.91x | 49% | 437370.153 | +290.8% |
| getTablesWarm | 16 | 1815720.611 | 1346458.056 | 4.15x | 26% | 474779.164 | +282.4% |
| iterateResultSet | 1 | 0.284 | 0.065 | 1.00x | 100% | 0.263 | +8.1% |
| iterateResultSet | 2 | 0.657 | 0.205 | 2.31x | 115% | 0.598 | +9.9% |
| iterateResultSet | 4 | 1.325 | 0.365 | 4.66x | 116% | 1.317 | +0.6% |
| iterateResultSet | 8 | 2.774 | 0.721 | 9.75x | 122% | 2.779 | -0.2% |
| iterateResultSet | 16 | 5.675 | 0.574 | 19.95x | 125% | 5.800 | -2.2% |
| submitToFirstRow | 1 | 0.985 | 0.473 | 1.00x | 100% | 0.884 | +11.4% |
| submitToFirstRow | 2 | 2.354 | 0.844 | 2.39x | 119% | 2.125 | +10.8% |
| submitToFirstRow | 4 | 4.774 | 8.686 | 4.85x | 121% | 4.962 | -3.8% |
| submitToFirstRow | 8 | 10.370 | 2.630 | 10.53x | 132% | 10.445 | -0.7% |
| submitToFirstRow | 16 | 21.431 | 5.094 | 21.76x | 136% | 22.275 | -3.8% |

**Scaling** is throughput at N threads over throughput at 1 thread; **Efficiency** is that
divided by N. Efficiency well under 100% is normal — BigQuery, the network and the runner's
core count all cap it. The failure this watches for is a curve that *collapses*, where total
throughput stops rising with threads at all: that is the shape of #98.
