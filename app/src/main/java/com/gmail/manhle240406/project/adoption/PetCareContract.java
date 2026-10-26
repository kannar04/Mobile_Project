package com.gmail.manhle240406.project.adoption;
import java.time.*;
import java.util.*;
import com.gmail.manhle240406.project.pet.PetSchema;

public final class PetCareContract {
    private PetCareContract(){}
    public static Map<String,Object> unknown(){return PetSchema.map("healthState","unknown","color","","behaviorNotes","","careNeeds","","sterilization","unknown","vaccinations",Collections.emptyList());}
    public static void validate(Map<String,Object> payload,String medication,LocalDate today){
        if(!PetSchema.keys(payload,"healthState","color","behaviorNotes","careNeeds","sterilization","vaccinations")||!Arrays.asList("unknown","no_concerns_reported","concerns_reported").contains(payload.get("healthState"))||!Arrays.asList("unknown","yes","no").contains(payload.get("sterilization")))throw new IllegalArgumentException("care_schema");
        for(String key:Arrays.asList("color","behaviorNotes","careNeeds")){Object value=payload.get(key);if(!(value instanceof String))throw new IllegalArgumentException("care_text");AdoptionValues.text((String)value,key.equals("color")?120:1000);}AdoptionValues.text(medication,1000);
        if(!(payload.get("vaccinations") instanceof List)||((List<?>)payload.get("vaccinations")).size()>20)throw new IllegalArgumentException("vaccination_limit");Set<String> ids=new HashSet<>();
        for(Object item:(List<?>)payload.get("vaccinations")){
            if(!(item instanceof Map))throw new IllegalArgumentException("vaccination_schema");Map<?,?> v=(Map<?,?>)item;
            if(!v.keySet().equals(new HashSet<>(Arrays.asList("entryId","typeLabel","reportedDate","datePrecision","provenance")))||!"owner_report".equals(v.get("provenance"))||!PetSchema.id(v.get("entryId"))||!ids.add((String)v.get("entryId"))||!(v.get("typeLabel") instanceof String))throw new IllegalArgumentException("vaccination_schema");AdoptionValues.text((String)v.get("typeLabel"),80);if(((String)v.get("typeLabel")).isEmpty())throw new IllegalArgumentException("vaccine_type");
            String precision=(String)v.get("datePrecision");Object date=v.get("reportedDate");if("unknown".equals(precision)){if(date!=null)throw new IllegalArgumentException("date_precision");continue;}if(!(date instanceof String))throw new IllegalArgumentException("date_precision");
            LocalDate floor;try{switch(precision){case "exact":if(!((String)date).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();floor=LocalDate.parse((String)date);break;case "month":if(!((String)date).matches("[0-9]{4}-[0-9]{2}"))throw new IllegalArgumentException();floor=YearMonth.parse((String)date).atDay(1);break;case "year":if(!((String)date).matches("[0-9]{4}"))throw new IllegalArgumentException();floor=Year.parse((String)date).atDay(1);break;default:throw new IllegalArgumentException();}}catch(RuntimeException bad){throw new IllegalArgumentException("invalid_reported_date");}
            if(floor.getYear()<1||floor.isAfter(today))throw new IllegalArgumentException("future_administration");
        }
        if(AdoptionValues.canonical(PetSchema.map("payload",payload,"medicationNotes",medication)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException("care_payload_limit");
    }
}
