edge 0 1 lossRate 0.0 delay 200 bw 10000 bt 1000
time + 5

# server port backlog [servint workint sz]
0 server 21 3

time + 5
# transfer dest port localPort amount [interval sz]
1 transfer 0 21 40 10000
time + 1000000
time + 10
exit
