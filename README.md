# pidu-r2dbc-pool-performance-test
Test showing  aproblem with colocation in r2dbc-pool

THere are 2 test cases. Each runs some heavy query and several small queries. Max connection pool size is set to 4, min size also set to 4 (we want to have always max connections available).

- First test case runs queries with defaults. Notice how only 1 thread is used and note average time.
- Second query disables colocation. It also sets max workers size to 4, to show that you only need as many workers as there are connectios. Without colocation and with enough workers, each socket can have its own thread. Note how all workers are used simultaneously and average time is significantly smaller. 
