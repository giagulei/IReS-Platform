SELECT	l_partkey AS agg_partkey, 
	0.2 * AVG(l_quantity) AS avg_quantity,
	l_extendedprice AS agg_extendedprice
FROM lineitem
GROUP BY l_partkey
