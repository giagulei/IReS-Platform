#!/bin/bash

<<INFO
Author				: Papaioannou Vassileios
Last update			: 28/ 09/ 2015
Previous updates	: none
Host System			: Ubuntu >= 10.04 LTS
INFO

<<DESCRIPTION
Necessary path definitions for the preprocessing step and all the experiments
DESCRIPTION

#definitions
##have text formating like colors; the period '.' is used instead of 'source'
##because it is POSIX compliant
#. resources/textformat/colors.sh
. colors.sh
##cluster slave nodes
SLAVES=(1 2) # 3 4 5) # 6 7 8 9 10)
#Home directory of used resources from this script
HOME=/home/hadoop
#Home output data directory
HOME_OUT=$HOME/outputData
##Output results
###a file where time statistics are stored
FILE_TIMES_TXT=$HOME_OUT/executionTimes.txt
###a file where job ids are stored
FILE_JOBIDS_TXT=$HOME_OUT/jobIDs.txt
###store job's record input and output
FILE_RECORDSIO_TXT=$HOME_OUT/recordsIO.txt
###know which jobs have failed
FILE_FAILED_JOBS_TXT=$HOME_OUT/failedJobs.txt
##Hadoop
##Hadoop 1.x definitions
###Hadoop home directory in local file system
HOME_HADOOP=$HOME/hadoop
###Hadoop home directory in HDFS
HOME_HDFS=/user/hadoop/optimizingJoins
###Hadoop execution home directory
HOME_HADOOP_EXECUTION=$HOME_HADOOP/bin/hadoop
##Hadoop 2.x (Yarn) definitions
###Hadoop home directory in local file system
HOME_YARN=$HOME/yarn
###Hadoop home directory in HDFS
HOME_HDFS=/user/hadoop/yarn
###Hadoop hadoop command home directory
HOME_YARN_EXECUTION=$HOME_YARN/bin/hadoop
###Hadoop format namenode
HOME_YARN_FORMAT=$HOME_YARN/bin/hdfs
###Hadoop mapred execution home directory
YARN_JAR_EXECUTION=$HOME_YARN/bin/yarn
