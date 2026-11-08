#!/usr/bin/env python3
"""Check the baseline Cleanroom IMM bridge on a real native Windows window."""
from pathlib import Path
import hashlib,os,subprocess,urllib.request

ROOT=Path(__file__).resolve().parents[1]
def main():
    if os.name!='nt':raise SystemExit('Windows native check requires Windows')
    folder=ROOT/'build/windows-ime';folder.mkdir(parents=True,exist_ok=True)
    artifacts=[
        ('cleanroom-0.4.0.jar','https://maven.cleanroommc.com/com/cleanroommc/cleanroom/0.4.0/cleanroom-0.4.0-universal.jar','703121f60cd20363752320b9574f927084d0cd69973a0aff24ce3fbea9dbb351'),
        ('jna-5.18.1.jar','https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.18.1/jna-5.18.1.jar','260c4b1e22b1db9e110ee441c4f13ce115f841fa48c41d78750986214b395557'),
        ('jna-platform-5.18.1.jar','https://repo.maven.apache.org/maven2/net/java/dev/jna/jna-platform/5.18.1/jna-platform-5.18.1.jar','ad14c1b1ec4f43d396231219dfa635ebf828f738eac9f890ea1bc07795892d9a'),
    ]
    for name,url,expected in artifacts:
        path=folder/name
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=expected:
            request=urllib.request.Request(url,headers={'User-Agent':'FTBLib-Cleanroom-baseline-verification'})
            with urllib.request.urlopen(request,timeout=60) as response: data=response.read()
            assert hashlib.sha256(data).hexdigest()==expected,name
            path.write_bytes(data)
    classpath=os.pathsep.join(str(folder/name) for name,_,_ in artifacts)
    subprocess.run(['javac','-encoding','UTF-8','-cp',classpath,'-d',str(folder),str(ROOT/'ftb-library/verification/windows/WindowsImeCheck.java')],check=True)
    result=subprocess.run(['java','--enable-native-access=ALL-UNNAMED','-cp',classpath+os.pathsep+str(folder),'dev.ftblib.verification.WindowsImeCheck'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (folder/'result.txt').write_text(result.stdout,encoding='utf-8');print(result.stdout,flush=True)
    result.check_returncode()
    assert 'WINDOWS_IME_CONTEXT_PASS' in result.stdout
if __name__=='__main__':main()
