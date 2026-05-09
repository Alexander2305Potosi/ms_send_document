package com.example.fileprocessor.infrastructure.helpers;

import com.example.fileprocessor.domain.port.out.MimeTypeResolver;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

/**
 * Infrastructure implementation of MimeTypeResolver using Spring's MediaTypeFactory.
 */
@Component
public class SpringMimeTypeResolver implements MimeTypeResolver {

    @Override
    public String resolve(String filename) {
        return MediaTypeFactory.getMediaType(filename)
                .map(Object::toString)
                .orElse("application/octet-stream");
    }
}
