import {hash,fail,MAX_BYTES} from './media.mjs';
/** Inject sharp and a generation-checked object port. No network/service setup here. */
export class ImageSanitizer {
 constructor(sharp,objects){this.sharp=sharp;this.objects=objects;}
 async sanitize(input){
  const object=await this.objects.readGeneration(input.stagingPath,input.generation,MAX_BYTES);
  const bytes=object.bytes;
  if(!Buffer.isBuffer(bytes)||bytes.length<1||bytes.length>MAX_BYTES||hash(bytes)!==input.inputHash)fail('invalid_bytes');
  const png=bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));
  const jpeg=bytes[0]===255&&bytes[1]===216&&bytes[2]===255;
  if((!png&&!jpeg)||(png?object.contentType!=='image/png':object.contentType!=='image/jpeg'))fail('mime_mismatch');
  const options={limitInputPixels:20_000_000,failOn:'warning',animated:false};
  const metadata=await this.sharp(bytes,options).metadata();
  if(!['jpeg','png'].includes(metadata.format)||(metadata.pages??1)!==1||(metadata.width??0)<1||(metadata.height??0)<1||metadata.width*metadata.height>20_000_000)fail('invalid_image');
  const variants={};
  for(const [name,edge] of [['display',2048],['thumbnail',320]]){
   // No keepMetadata/withMetadata; fresh JPEG strips EXIF/GPS/ICC/source filename.
   const {data,info}=await this.sharp(bytes,options).timeout({seconds:10}).rotate().resize(edge,edge,{fit:'inside',withoutEnlargement:true}).flatten({background:'#ffffff'}).jpeg({quality:85}).toBuffer({resolveWithObject:true});
   if(data.length>MAX_BYTES)fail('encoded_size');
   const path=`final/${input.ownerUid}/${input.assetId}/${name}.jpg`;
   // Port must create immutably or reconcile the SAME sanitized bytes/hash on retry,
   // record unfinished generations for orphan repair, and NEVER mint download tokens.
   const generation=await this.objects.createImmutable(path,data,{contentType:'image/jpeg',inputGeneration:input.generation,sha256:hash(data)});
   variants[name]={path,generation:String(generation),width:info.width,height:info.height,bytes:data.length};
  }
  return variants;
 }
 async deleteOriginal({path,generation}){await this.objects.deleteGeneration(path,generation);}
}
