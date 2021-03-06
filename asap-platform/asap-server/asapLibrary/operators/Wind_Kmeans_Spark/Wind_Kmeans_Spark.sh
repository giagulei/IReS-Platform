#!/bin/bash

<<INFO
Author				: Papaioannou Vassilis
Last update			: 29/ 01/ 2016 
Previous updates	: none
Host System			: Ubuntu
Hadoop				: 2.7.1
INFO

<<DESCRIPTION
pyspark clustering.py <region> <timeframe> <archetipi> <k> <percentage>

Input parameters:

region	 	: a string containing the name of the region related to the dataset
timeframe	: a string containing the period related to the dataset
archetipi	: a csv files containing typical calling profiles for each label.
				E.g.: Resident->typical resident profiles, etc..
k		 	: the number of centroids to be computed
percentage	: the percentage of profiles to use for centroids computation

Output:

It stores a files “centroids<region>-<timeframe>” containing the association
between each centroid and the user type. E.g. Centroid1->resident, etc
DESCRIPTION

source /home/forth/asap-venv/bin/activate

echo -e "Starting clustering.py script ..."
SPARK_PORT=$1
OPERATOR=$2
REGION=$3
TIMEFRAME=$4
SPARK_HOME=/home/forth/asap4all/spark-1.5.2-bin-hadoop2.6
$SPARK_HOME/bin/spark-submit --master $SPARK_PORT $OPERATOR $REGION $TIMEFRAME
echo -e "... clustering.py script ended"
