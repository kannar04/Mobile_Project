"""Select unchanged approved rule blocks for wired client features; all other paths deny.
No new authorization expressions are generated. Run when canonical rules change.
"""
from pathlib import Path
import re, json, hashlib

root=Path(__file__).resolve().parents[1]
source=(root/'firestore.rules').read_text(encoding='utf-8')
blocks=[]
for match in re.finditer(r'^    (function|match)\s+[^\n]+',source,re.M):
    start=match.start();kind=match.group(1)
    opening=(start+re.search(r'\s+\{',match.group(0)).end()-1) if kind=='match' else source.index('{',start)
    pos=opening;depth=0
    while pos<len(source):
        if source.startswith('//',pos):
            end=source.find('\n',pos);pos=len(source) if end<0 else end+1;continue
        if source[pos] in ['"',"'"]:
            quote=source[pos];pos+=1
            while pos<len(source):
                if source[pos]=='\\':pos+=2;continue
                if source[pos]==quote:pos+=1;break
                pos+=1
            continue
        if source[pos]=='{':depth+=1
        elif source[pos]=='}':
            depth-=1
            if depth==0:pos+=1;break
        pos+=1
    blocks.append((kind,source[start:pos]))

roots={'communityMemberships','profileModeration','publicProfileControls','publicProfiles','securitySubjects',
       'pets','petOfferAuthorities','petListingSlots','petActivityGates','foundationChecks','listings',
       'communityPosts','communityFeed','communityPublicFeed','communityCounts','communityCommentFeed','reports','reportRateControls',
       'catalogSpecies','catalogBreeds','catalogRegions','listingDiscovery','discoveryPolicy','system',
       'reservationCommunication','conversationBlocks','conversations'}
user_parts={'profileDraft','private','profileOperations','profileReviews','petPrivate','petChallenges','petReceipts',
            'listingDrafts','communityOperations','reportStatus','reportLinks','reportOperations','notificationInbox',
            'notificationPreferences','favorites','favoriteMeta','favoriteOperations','closureViews','conversationIndex','moderationNotices'}
selected=[]
for kind,text in blocks:
    if kind!='match':continue
    path=re.match(r'\s*match\s+(.*?)\s+\{',text).group(1)
    parts=path.strip('/').split('/')
    if parts[0] in roots or (parts[0]=='users' and (len(parts)==2 or len(parts)>2 and parts[2] in user_parts)):
        selected.append(text)
functions={re.match(r'\s*function\s+(\w+)',text).group(1):text for kind,text in blocks if kind=='function'}
used=set()
while True:
    calls=set(re.findall(r'\b(\w+)\s*\(', '\n'.join(selected+[functions[n] for n in used])))
    next_used=used | (calls & functions.keys())
    if next_used==used:break
    used=next_used
kept=[text for kind,text in blocks if kind=='function' and re.match(r'\s*function\s+(\w+)',text).group(1) in used]
out="rules_version = '2';\nservice cloud.firestore {\n  match /databases/{database}/documents {\n"
out+='\n'.join(kept+selected)+"\n    match /{document=**} { allow read, write: if false; }\n  }\n}\n"
(root/'firestore.spark.rules').write_text(out,encoding='utf-8')
(root/'build/spark-rule-selection.json').write_text(json.dumps({'sourceSha256':hashlib.sha256((root/'firestore.rules').read_bytes()).hexdigest(),
    'selectedSha256':hashlib.sha256((root/'firestore.spark.rules').read_bytes()).hexdigest(),'matches':len(selected),'functions':len(used),
    'unchangedBlocks':True,'excludedPathsDefaultDeny':True},indent=2),encoding='utf-8')
print('Spark Rules: unchanged blocks',len(selected),'functions',len(used),'bytes',len(out.encode()))
