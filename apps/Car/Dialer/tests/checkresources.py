#!/usr/bin/python3
import subprocess
import sys

process = subprocess.Popen(['lint', '--check', 'UnusedResources', sys.argv[1]],
                          stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE)

stdout, stderr = process.communicate()

lines = stdout.decode('utf-8').split('\n')
results = []
for i in range(len(lines)-1):
    if '[UnusedResources]' in lines[i] and 'msgid=' not in lines[i+1]:
        results.append(lines[i])

if len(results) > 0:
    print('\n'.join(results))
    sys.exit(1)
else:
    sys.exit(0)

