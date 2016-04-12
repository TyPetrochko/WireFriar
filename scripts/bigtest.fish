# edge 0 1 lossRate 0 delay 0 bw 10000 bt 1000 (Bps = 6250)
# edge 0 1 lossRate 0 delay 200 bw 10000 bt 1000 (Bps = 4545)
# edge 0 1 lossRate 0.2 delay 0 bw 10000 bt 1000 (Bps = ~900 - ~1500)
# edge 0 1 lossRate 0.2 delay 200 bw 10000 bt 1000 (Bps = ~150 - ~250)
# edge 0 1 lossRate 0.2 delay 200 bw 10000 bt 1000
edge 0 1 lossRate 0.0 delay 4 bw 10000 bt 1000
edge 0 2 lossRate 0.1 delay 500 bw 10000 bt 1000
edge 0 3 lossRate 0.05 delay 10 bw 10000 bt 1000
edge 0 4 lossRate 0.25 delay 5 bw 10000 bt 1000
edge 0 5 lossRate 0.05 delay 300 bw 10000 bt 1000
edge 0 6 lossRate 0.0 delay 200 bw 10000 bt 1000
time + 5
# server port backlog [servint workint sz]
0 server 21 1
time + 5
# transfer dest port localPort amount [interval sz]
1 transfer 0 21 40 50000
2 transfer 0 21 40 50000
3 transfer 0 21 40 50000
4 transfer 0 21 40 50000
5 transfer 0 21 40 50000
6 transfer 0 21 40 50000
time + 10000000
time + 10
exit
