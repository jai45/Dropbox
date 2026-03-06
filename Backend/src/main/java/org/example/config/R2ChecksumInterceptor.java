package org.example.config;

import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;

/**
 * Strips all {@code x-amz-checksum-*} and {@code x-amz-sdk-checksum-algorithm}
 * headers before the request is sent to Cloudflare R2.
 *
 * R2 does not implement the AWS SDK v2 HTTP-checksum extension and rejects any
 * request that contains those headers / XML elements with a 400 "XML not well formed".
 */
public class R2ChecksumInterceptor implements ExecutionInterceptor {

    @Override
    public SdkHttpRequest modifyHttpRequest(Context.ModifyHttpRequest context,
                                            ExecutionAttributes executionAttributes) {
        SdkHttpRequest.Builder builder = context.httpRequest().toBuilder();

        context.httpRequest().headers().keySet().stream()
                .filter(h -> h.toLowerCase().startsWith("x-amz-checksum")
                          || h.equalsIgnoreCase("x-amz-sdk-checksum-algorithm"))
                .forEach(builder::removeHeader);

        return builder.build();
    }
}
