from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1]
target=root/'functions/domain'
target.mkdir(exist_ok=True)
for source in (root/'firebase-domain').glob('*.mjs'):shutil.copyfile(source,target/source.name)
print('Synchronized pure domain modules for managed Firebase deployment.')
