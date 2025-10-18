
package com.baksart.Note2TexBack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProps {
    private String backendPublicBaseUrl;

    public static class Frontend {
        private String verifyRedirect;
        private String resetRedirect;

        public String getVerifyRedirect() { return verifyRedirect; }
        public void setVerifyRedirect(String v) { this.verifyRedirect = v; }
        public String getResetRedirect() { return resetRedirect; }
        public void setResetRedirect(String r) { this.resetRedirect = r; }
    }

    private Frontend frontend = new Frontend();


    public String getBackendPublicBaseUrl() { return backendPublicBaseUrl; }
    public void setBackendPublicBaseUrl(String v) { this.backendPublicBaseUrl = v; }
    public Frontend getFrontend() { return frontend; }
}
