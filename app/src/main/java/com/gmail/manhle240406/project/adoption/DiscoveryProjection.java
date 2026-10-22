package com.gmail.manhle240406.project.adoption;
import java.util.*;
import com.google.firebase.Timestamp;
import com.gmail.manhle240406.project.pet.PetSchema;

/** Fail closed before handing query data to a consumer. Trusted writers must apply the same whitelist. */
public final class DiscoveryProjection {
    private DiscoveryProjection(){}
    public static void validate(Map<String,Object> d,String id){
        if(Long.valueOf(2).equals(d.get("schemaVersion"))){if(d.size()!=26||!Arrays.asList(2L,3L).contains(d.get("sourceSchemaVersion"))||!(d.get("careDigest") instanceof String)||!((String)d.get("careDigest")).matches("[0-9a-f]{64}"))throw new IllegalArgumentException("discovery_schema2");Map<String,Object> legacy=new HashMap<>(d);legacy.remove("sourceSchemaVersion");legacy.remove("careDigest");legacy.put("schemaVersion",1L);validate(legacy,id);return;}
        if(!PetSchema.keys(d,"schemaVersion","listingId","petId","ownerUid","contentVersion","publicationVersion","audience","state","firstPublishedAt","updatedAt","title","petName","nameKey","speciesId","breedId","regionId","regionLabel","sex","ageBand","offerType","availability","careCues","bundledIllustrationKey","mediaDescriptors")
            ||!Long.valueOf(1).equals(d.get("schemaVersion"))||!id.equals(d.get("listingId"))||!PetSchema.id(id)||!PetSchema.id(d.get("petId"))||!(d.get("ownerUid") instanceof String)||!PetSchema.number(d.get("contentVersion"),1)||!PetSchema.number(d.get("publicationVersion"),1)||!"signed_in".equals(d.get("audience"))||!"visible".equals(d.get("state"))||!(d.get("firstPublishedAt") instanceof Timestamp)||!(d.get("updatedAt") instanceof Timestamp))throw new IllegalArgumentException("discovery_schema");
        for(String field:Arrays.asList("title","petName","regionLabel")){Object value=d.get(field);if(!(value instanceof String))throw new IllegalArgumentException("discovery_text");AdoptionValues.text((String)value,field.equals("title")?120:80);}
        if(((String)d.get("title")).isEmpty()||!(d.get("nameKey") instanceof String)||!((String)d.get("nameKey")).matches("[a-z0-9 ]{0,80}"))throw new IllegalArgumentException("name_key");
        for(String field:Arrays.asList("speciesId","breedId","regionId"))if(d.get(field)!=null)AdoptionValues.catalogId((String)d.get(field));
        if(!Arrays.asList("unknown","male","female").contains(d.get("sex"))||!Arrays.asList("unknown","baby","young","adult","senior").contains(d.get("ageBand"))||!"adoption".equals(d.get("offerType"))||!Arrays.asList("available","reserved").contains(d.get("availability"))||!(d.get("mediaDescriptors") instanceof List)||!((List<?>)d.get("mediaDescriptors")).isEmpty()||d.get("bundledIllustrationKey")!=null&&!Arrays.asList("default","cat","dog").contains(d.get("bundledIllustrationKey")))throw new IllegalArgumentException("discovery_values");
        Object cues=d.get("careCues");if(!(cues instanceof Map)||!((Map<?,?>)cues).keySet().equals(new HashSet<>(Arrays.asList("source","healthState")))||!"owner_report".equals(((Map<?,?>)cues).get("source"))||!Arrays.asList("unknown","no_concerns_reported","concerns_reported").contains(((Map<?,?>)cues).get("healthState")))throw new IllegalArgumentException("care_cues");
    }
}
