package org.thisway.support.logging.masking;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;

public class MaskingIntroSpector extends NopAnnotationIntrospector {

    @Override
    public JsonSerializer<?> findSerializer(Annotated annotated) {
        if (annotated == null) {
            return null;
        }

        MaskingData maskingData = annotated.getAnnotation(MaskingData.class);
        if (maskingData == null) {
            return null;
        }

        return new MaskingSerializer(maskingData.value());
    }
}
