#!/usr/bin/env python3
"""Verify the three FTB modules and the optional exact-baseline server patch."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
def sha(path):
 with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def main():
 config=json.loads((ROOT/'release.json').read_text());dest=ROOT/'build/release'
 if dest.exists():shutil.rmtree(dest)
 dest.mkdir(parents=True)
 if os.environ.get('GITHUB_REF_TYPE')=='tag' and os.environ.get('GITHUB_REF_NAME')!=config['tag']:raise SystemExit('Release tag does not match release.json')
 if config['prerelease'] is not False:raise SystemExit('This release must be stable')
 counts={};entries={}
 for module,name in config['modules'].items():
  for suffix in ['', '-sources']:
   source=ROOT/module/'build/libs'/(name+suffix+'.jar');assert source.is_file(),source
   with zipfile.ZipFile(source) as jar:
    assert jar.testzip() is None
    if not suffix:
     paths=jar.namelist()
     assert not any('/acceptance/' in p or p.startswith(('dev/yudream/media/','dev/yudream/mcp/','net/createmod/')) for p in paths)
     assert not any('watermedia' in p.lower() for p in paths)
     entries[source.name]={p:hashlib.sha256(jar.read(p)).hexdigest() for p in paths if not p.endswith('/')}
     assert 'ModType: CRL' in jar.read('META-INF/MANIFEST.MF').decode()
     if module=='ftb-quests':
      raw=jar.read('com/feed_the_beast/ftbquests/FTBQuests.class')
      assert b'ftblib@[5.5.4-modern.4,)' in raw and b'after:ponder-legacy' not in raw
     if module=='server-compat':
      assert 'FMLCorePlugin: local.ae2addons.cleanroom040.ServerCompatibilityPlugin' in jar.read('META-INF/MANIFEST.MF').decode()
   shutil.copyfile(source,dest/source.name)
  tests=[]
  for report in (ROOT/module/'build/test-results/test').glob('TEST-*.xml'):
   e=ET.parse(report).getroot();assert all(int(e.get(k,'0'))==0 for k in ['failures','errors','skipped']);tests.append(int(e.get('tests')))
  counts[module]=sum(tests)
 assert counts['ftb-quests']>=87 and counts['ftb-library']>=10 and counts['server-compat']>=2,counts
 try:commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip()
 except subprocess.CalledProcessError:commit='uncommitted-local-candidate'
 record={'repository':'mcyudream/ftb-quests-legacy','commit':commit,'release':config,'tests':counts,'production_entries_sha256':entries}
 (dest/'BUILD-INFO.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n')
 shutil.copyfile(ROOT/'docs/releases'/(config['tag'].removeprefix('v')+'.md'),dest/'RELEASE-NOTES.md')
 files=sorted(p for p in dest.iterdir() if p.is_file() and p.name!='SHA256SUMS')
 (dest/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in files))
 print(json.dumps({'files':[p.name for p in files],'tests':counts,'production_sha256':{p.name:sha(p) for p in files if p.suffix=='.jar' and '-sources' not in p.name}},indent=2))
if __name__=='__main__':main()
