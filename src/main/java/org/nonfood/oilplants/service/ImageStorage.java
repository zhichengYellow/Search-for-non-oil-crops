package org.nonfood.oilplants.service;

import jakarta.servlet.http.Part;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

public final class ImageStorage {
    public record Stored(String name,String contentType){}
    private ImageStorage(){}
    public static Stored save(Part part)throws IOException{
        if(part==null||part.getSize()==0)throw new IllegalArgumentException("请选择图片文件。");if(part.getSize()>5L*1024*1024)throw new IllegalArgumentException("图片不能超过 5 MB。");
        byte[] head;try(InputStream in=part.getInputStream()){head=in.readNBytes(16);}String ext=null,type=null;
        if(head.length>=8&&(head[0]&255)==137&&head[1]==80&&head[2]==78&&head[3]==71){ext="png";type="image/png";}
        else if(head.length>=3&&(head[0]&255)==255&&(head[1]&255)==216&&(head[2]&255)==255){ext="jpg";type="image/jpeg";}
        else if(head.length>=12&&new String(head,0,4).equals("RIFF")&&new String(head,8,4).equals("WEBP")){ext="webp";type="image/webp";}
        else throw new IllegalArgumentException("仅支持有效的 JPG、PNG 或 WebP 图片。");
        Path dir=uploadDir();Files.createDirectories(dir);String name=UUID.randomUUID()+"."+ext;
        try(InputStream in=part.getInputStream()){Files.copy(in,dir.resolve(name),StandardCopyOption.REPLACE_EXISTING);}return new Stored(name,type);
    }
    public static Path uploadDir(){String configured=System.getenv("UPLOAD_DIR");return Path.of(configured==null||configured.isBlank()?System.getProperty("java.io.tmpdir")+"/oil-plants-uploads":configured).toAbsolutePath().normalize();}
    public static void delete(String name)throws IOException{Files.deleteIfExists(resolve(name));}
    public static Path resolve(String name)throws IOException{
        if(name==null||!name.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)"))throw new FileNotFoundException();Path base=uploadDir(),p=base.resolve(name).normalize();if(!p.startsWith(base))throw new FileNotFoundException();return p;
    }
}
