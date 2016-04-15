edge 0 1 lossRate 0.0 delay 200 bw 10000 bt 100
edge 0 2 lossRate 0.0 delay 200 bw 10000 bt 100
0 trace on
time + 5
0 server 21 3
time + 5
1 debug off
1 transfer 0 21 40 8000
time + 1000000
time + 10
2 congestion-control on
2 transfer 0 21 40 8000
time + 1000000
time + 10
exit
