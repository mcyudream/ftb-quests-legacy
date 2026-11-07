package com.feed_the_beast.ftbquests.util;

/** Existing component IDs stay valid; an optional fragment selects one scene/tutorial. */
public record PonderTarget(String component,String scene)
{
    public static PonderTarget parse(String value)
    {
        if(value==null||value.length()>512)throw new IllegalArgumentException("Invalid Ponder target");
        String target=value.strip();
        // A component may itself use the ponder namespace. Only a full URI has a second colon.
        int fragment=target.indexOf('#');
        String component=fragment<0?target:target.substring(0,fragment);
        if(component.startsWith("ponder:")&&component.indexOf(':',7)>=0)target=target.substring(7);
        String[] parts=target.split("#",-1);
        if(parts[0].indexOf(':')<0)parts[0]="minecraft:"+parts[0];
        else if(parts[0].startsWith(":"))parts[0]="minecraft"+parts[0];
        int separator=parts[0].indexOf(':');
        parts[0]=parts[0].substring(0,separator).toLowerCase(java.util.Locale.ROOT)+parts[0].substring(separator);
        if(parts.length>2||!valid(parts[0])||(parts.length==2&&!valid(parts[1])))throw new IllegalArgumentException("Invalid Ponder target");
        return new PonderTarget(parts[0],parts.length==2?parts[1]:"");
    }
    private static boolean valid(String id)
    {
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))return false;
        for(String segment:id.substring(id.indexOf(':')+1).split("/",-1))if(segment.isEmpty()||segment.equals(".")||segment.equals(".."))return false;
        return true;
    }
    public String value(){return component+(scene.isEmpty()?"":"#"+scene);}
    public String uri(){return "ponder:"+value();}
}
