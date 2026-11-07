#!/usr/bin/env python3
"""Fetch SHA-256 pinned compile inputs into a repository-local, ignored cache."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib,json,time,urllib.request,os
ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'.build-deps'
def sha(path):
 with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def fetch(entry):
 target=CACHE/entry['file']
 if target.is_file() and sha(target)==entry['sha256']:return 'Verified '+target.name
 partial=target.with_name(target.name+'.part')
 for attempt in range(3):
  try:
   req=urllib.request.Request(entry['url'],headers={'User-Agent':'FTB-Quests-Legacy-build'})
   with urllib.request.urlopen(req,timeout=45) as response,partial.open('wb') as out:
    while data:=response.read(131072):out.write(data)
   if sha(partial)!=entry['sha256']:raise ValueError('SHA-256 mismatch: '+target.name)
   partial.replace(target);return 'Downloaded '+target.name
  except Exception:
   partial.unlink(missing_ok=True)
   if attempt==2:raise
   time.sleep(attempt+1)
def main():
 CACHE.mkdir(exist_ok=True)
 entries=json.loads((ROOT/'ftb-quests/dependencies.lock.json').read_text())
 lock=json.loads((ROOT/'ftb-quests/ponder-compat.lock.json').read_text())
 entries.append({'file':lock['sdk_file'],'sha256':lock['sdk_sha256'],'url':lock['sdk_url']})
 with ThreadPoolExecutor(max_workers=4) as pool:
  for result in pool.map(fetch,entries):print(result,flush=True)
if __name__=='__main__':main()
