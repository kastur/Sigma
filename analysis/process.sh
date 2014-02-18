for trace in `ls *.trace`;
do
  echo processing $trace
  dmtracedump $trace -g $trace.png > /dev/null
  dmtracedump $trace -h > $trace.html
done
