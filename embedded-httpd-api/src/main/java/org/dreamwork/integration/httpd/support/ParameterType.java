package org.dreamwork.integration.httpd.support;

public enum ParameterType {
    integer, long_integer, bool, string, datetime, raw,
    /** @since 1.1.0 */
    request_attribute,
    /** @since 1.1.0 */
    session_attribute,
    /** @since 1.1.0 */
    context_attribute,
}
