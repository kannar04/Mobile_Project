package com.gmail.manhle240406.project.adoption;
import java.util.*;
/** Narrow optional experience feedback, no moral/welfare score or default five stars. */
public final class ExperienceReviewContract {
 private ExperienceReviewContract(){}
 public static void validate(Map<String,Object> input){QuestionnaireContract.keys(input,"communication","preparation","disclosureAccuracy","comment");boolean meaningful=false;for(String key:Arrays.asList("communication","preparation","disclosureAccuracy")){Object score=input.get(key);if(score!=null){if(!(score instanceof Long)||(Long)score<1||(Long)score>5)throw new IllegalArgumentException("review_rating");meaningful=true;}}AdoptionValues.text((String)input.get("comment"),1000);if(!meaningful&&((String)input.get("comment")).isEmpty())throw new IllegalArgumentException("empty_review");}
}
