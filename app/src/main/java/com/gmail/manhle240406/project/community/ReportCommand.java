package com.gmail.manhle240406.project.community;
import java.util.*;
import com.gmail.manhle240406.project.pet.PetSchema;
/** Private exact retry input is memory only. IDs may be checkpointed without text. */
public final class ReportCommand {
    public static final List<String> CATEGORIES=Arrays.asList("spam","harassment","misleading_information","privacy","animal_welfare","other");
    public final String operationId,reportId,command,kind,targetId,parentId;
    public final long observedVersion,baseVersion;
    public final Map<String,Object> input;
    public ReportCommand(String op,String rid,String command,String kind,String target,String parent,long observed,long base,String category,String statement,String evidence){
        if(!PetSchema.id(op)||!PetSchema.id(rid)||!PetSchema.id(target)||!Arrays.asList("post","comment").contains(kind)||kind.equals("comment")&&!PetSchema.id(parent)
            ||kind.equals("post")&&parent!=null||!Arrays.asList("submit","append","withdraw").contains(command)||observed<1||base<0||!CATEGORIES.contains(category)
            ||statement==null||statement.codePointCount(0,statement.length())>1000||statement.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4000
            ||(statement.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+(evidence==null?0:evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)>6144)
            ||category.equals("other")&&statement.isBlank()||evidence!=null&&!SocialRepository.text(evidence,1000,4000))throw new IllegalArgumentException("invalid");
        this.operationId=op;this.reportId=rid;this.command=command;this.kind=kind;this.targetId=target;this.parentId=parent;this.observedVersion=observed;this.baseVersion=base;
        input=Collections.unmodifiableMap(SocialRepository.map("category",category,"statement",statement,"evidenceText",evidence,"observedVersion",observed));
    }
    public String targetKey(){return kind+"_"+(parentId==null?"":parentId+"_")+targetId;}
    public boolean matches(Map<String,Object> r){return r!=null&&operationId.equals(r.get("operationId"))&&reportId.equals(r.get("reportId"))&&command.equals(r.get("command"))&&targetKey().equals(r.get("targetKey"))&&Long.valueOf(baseVersion).equals(r.get("baseVersion"))&&input.equals(r.get("input"));}
}
