# edge 0 1 lossRate 0 delay 0 bw 10000 bt 1000 (Bps = 6250)
# edge 0 1 lossRate 0 delay 200 bw 10000 bt 1000 (Bps = 4545)
# edge 0 1 lossRate 0.2 delay 0 bw 10000 bt 1000 (Bps = ~900 - ~1500)
# edge 0 1 lossRate 0.2 delay 200 bw 10000 bt 1000 (Bps = ~150 - ~250)
# edge 0 1 lossRate 0.2 delay 200 bw 10000 bt 1000
edge 0 1 lossRate 0.0 delay 0 bw 10000 bt 1000
edge 0 2 lossRate 0.0 delay 0 bw 10000 bt 1000
edge 0 3 lossRate 0.2 delay 100 bw 10000 bt 1000
time + 5
# server port backlog [servint workint sz]
0 server 21 1
time + 5
# transfer dest port localPort amount [interval sz]
1 transfer 0 21 40 50000
# 2 transfer 0 21 40 50000
3 transfer 0 21 40 50000
time + 10000000
time + 10
exit
