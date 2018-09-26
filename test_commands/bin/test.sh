sleep 3

sed 's/$/ 3.33/' ../input/input.pdb > ../output/scored.pdb

> ../output/result.csv
cat ../input/chain.txt >> ../output/result.csv;
cat ../input/chain.txt >> ../output/result.csv;
cat ../input/chain.txt >> ../output/result.csv;
sed -i 's/$/,3.33/' ../output/result.csv

sleep 2
