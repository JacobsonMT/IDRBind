pdbfile="$1"
chain="$2"
fasta="$3"

sed 's/$/ 3.33/' "$1" > IDRBind.pdb

echo "$2" > core_rim_and_graph.csv
cat "$3" >> core_rim_and_graph.csv

sleep 5
