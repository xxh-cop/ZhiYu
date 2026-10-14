package com.zhiyu.modules.file;
import java.io.*;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;
@Component
public class LocalMediaStorage implements MediaStorage {
 private final Path root;
 public LocalMediaStorage(@Value("${zhiyu.file.private-dir:./private-media}") String directory){root=Path.of(directory).toAbsolutePath().normalize();}
 private Path path(String key){if(!key.matches("[a-f0-9]{32}\\.mp4"))throw new IllegalArgumentException("Invalid media key");return root.resolve(key);}
 public void put(String key,InputStream input)throws IOException{Files.createDirectories(root);Files.copy(input,path(key));}
 public Resource read(String key)throws IOException{Path p=path(key);if(!Files.isRegularFile(p))throw new FileNotFoundException();return new FileSystemResource(p);}
 public void remove(String key)throws IOException{Files.deleteIfExists(path(key));}
}
