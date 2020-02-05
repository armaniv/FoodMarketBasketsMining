#!/bin/bash
# ------------------------------------------------------------------
# Author: Marian Alexandru Diaconu
# Email: marianalexandrudiaconu@gmail.com
#------------------------------------------------------------------

function Usage(){
  echo "Usage: "
  echo "./dbscanELKI.sh data=global_path_to_basket_scores_file eps=eps_value minPoints=min_points_value log=global_path_to_file_for_logs output=global_path_to_file_for_clustering_results"
  echo "All parameters are REQUIRED"
  echo ""
  echo "For help:"
  echo "./dbscanELKI.sh help"
  echo ''
  exit
}

function Info(){
  echo ''
  echo '---- ELKI DBSCAN interface ----'
  echo ''
  echo 'This script acts as an interface to ELKI DBSCAN library, which actually implements the algorithm.
  Basically this script collects parameters for a KDD (Knowledge Discovery in Databases) process and calls clustering.DBSCAN
  accessing ELKI JAR file. This script should not be moved because it works together with ELKI library, which should
  be provided in /lib folder.'
  echo ''
  Usage
}

# collect args
for arg in "$@"
do
  index=$(echo "$arg" | cut -f1 -d=)
  val=$(echo "$arg" | cut -f2 -d=)
  case $index in
    help) Info;;
    data) data=$val;;
    eps) eps=$val;;
    minPoints) minPoints=$val;;
    log) log=$val;;
    output) output=$val;;
    *)
  esac
done

# check args
if [ -z "$data" ] | [ -z "$eps" ] | [ -z "$minPoints" ] | [ -z "$log" ] | [ -z "$output" ]
then
  echo "---- Usage Error ----"
  Usage
fi

# access ELKI folder to avoid dependencies errors
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" || exit ; pwd -P )
cd "$parent_path" || exit
cd lib/elki-0.7.5 || exit

./elki.sh KDDCLIApplication -dbc.in "$data" -db.index "tree.spatial.kd.MinimalisticMemoryKDTree\$Factory" -time \
      -verbose \
      -algorithm clustering.DBSCAN \
      -dbscan.epsilon "$eps" \
      -dbscan.minpts "$minPoints" \
      -evaluator clustering.EvaluateClustering \
      -resulthandler ResultWriter,ClusteringVectorDumper -out "$log" \
      -clustering.output "$output"
