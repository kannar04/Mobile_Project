package com.gmail.manhle240406.project.adoption;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.gmail.manhle240406.project.listing.ListingSchema;
/** Typed, bounded care preparation; no score, automatic selection or durable answers. */
public final class QuestionnaireContract {
 private QuestionnaireContract(){}
 private static final Set<String> TOPICS=new HashSet<>(Arrays.asList("known_needs","care_routine","transport","supplies","support","experience","questions","needs_discussion"));
 static void keys(Map<String,Object> m,String... keys){if(m==null||!m.keySet().equals(new HashSet<>(Arrays.asList(keys))))throw new IllegalArgumentException("unknown_schema");}
 @SuppressWarnings("unchecked") public static Map<String,Object> answers(Map<String,Object> template,Map<String,Object> answers){
  keys(template,"schemaVersion","templateId","version","questions");if(!Long.valueOf(1).equals(template.get("schemaVersion"))||AdoptionValues.version(template,"version")<1)throw new IllegalArgumentException("template_schema");AdoptionValues.catalogId((String)template.get("templateId"));
  Object raw=template.get("questions");if(!(raw instanceof List)||((List<?>)raw).size()>8||answers==null)throw new IllegalArgumentException("question_limit");Set<String> seen=new HashSet<>();int required=0;
  for(Object row:(List<?>)raw){if(!(row instanceof Map))throw new IllegalArgumentException("question_schema");Map<String,Object> q=(Map<String,Object>)row;keys(q,"id","type","required","topic","purpose","labels","options");String id=AdoptionValues.catalogId((String)q.get("id"));if(!seen.add(id)||!TOPICS.contains(q.get("topic"))||!(q.get("required") instanceof Boolean))throw new IllegalArgumentException("question_schema");AdoptionValues.text((String)q.get("purpose"),200);if(((String)q.get("purpose")).isEmpty())throw new IllegalArgumentException("question_purpose");Map<String,Object> labels=(Map<String,Object>)q.get("labels");keys(labels,"vi","en");for(Object label:labels.values()){AdoptionValues.text((String)label,200);if(((String)label).isEmpty())throw new IllegalArgumentException("question_label");}
   boolean need=(Boolean)q.get("required");if(need)required++;String type=(String)q.get("type");Object opts=q.get("options");if(!(opts instanceof List)||((List<?>)opts).size()>8||new HashSet<>((List<?>)opts).size()!=((List<?>)opts).size())throw new IllegalArgumentException("question_options");List<?> options=(List<?>)opts;for(Object option:options)AdoptionValues.catalogId((String)option);
   if("tri_state".equals(type)){if(!options.equals(Arrays.asList("yes","no","needs_discussion")))throw new IllegalArgumentException("question_options");}else if("choice".equals(type)){if(!options.contains("needs_discussion"))throw new IllegalArgumentException("question_uncertainty");}else if(!"short_text".equals(type)||!options.isEmpty())throw new IllegalArgumentException("question_type");
   Object value=answers.get(id);if(value==null){if(answers.containsKey(id)||need)throw new IllegalArgumentException("required_answer");continue;}if("short_text".equals(type)){AdoptionValues.text((String)value,400);if(need&&((String)value).isEmpty())throw new IllegalArgumentException("required_answer");}else if(!options.contains(value))throw new IllegalArgumentException("answer_type");
  }
  if(required>6||!seen.containsAll(answers.keySet())||AdoptionValues.canonical(answers).getBytes(StandardCharsets.UTF_8).length>4096)throw new IllegalArgumentException("answers_limit");return ListingSchema.freeze(answers);
 }
}
