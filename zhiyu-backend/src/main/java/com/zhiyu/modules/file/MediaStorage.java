package com.zhiyu.modules.file;
import java.io.*;
import org.springframework.core.io.Resource;
/** Replace this implementation with an object-store adapter without changing business references. */
public interface MediaStorage {
 void put(String key, InputStream input) throws IOException;
 Resource read(String key) throws IOException;
 void remove(String key) throws IOException;
}
