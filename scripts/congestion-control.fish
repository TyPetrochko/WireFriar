edge 0 1 lossRate 0.0 delay 4 bw 5000 bt 500
edge 0 2 lossRate 0.1 delay 500 bw 5000 bt 500
edge 2 3 lossRate 0.05 delay 10 bw 5000 bt 500
edge 3 4 lossRate 0.15 delay 5 bw 5000 bt 500
edge 4 5 lossRate 0.05 delay 300 bw 5000 bt 500
edge 5 6 lossRate 0.0 delay 200 bw 2000 bt 500
edge 3 0 lossRate 0.0 delay 200 bw 1000 bt 500
edge 4 0 lossRate 0.0 delay 200 bw 1000 bt 500
edge 5 0 lossRate 0.0 delay 200 bw 1000 bt 500
edge 6 0 lossRate 0.0 delay 200 bw 1000 bt 500

time + 5
echo ------- Testing congestion control -------
0 trace off
0 congestion-control on
0 server 21 1

time + 5

1 transfer 0 21 40 5000
2 transfer 0 21 40 5000
3 transfer 0 21 40 5000
4 transfer 0 21 40 5000
5 transfer 0 21 40 5000
6 transfer 0 21 40 5000

time + 100000000
time + 10
exit
