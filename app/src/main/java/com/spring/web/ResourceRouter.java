package com.spring.web;

import jakarta.servlet.http.HttpServletRequest;

import javax.ws.rs.container.ResourceContext;

public interface ResourceRouter {
    OutboundResponse dispatch(HttpServletRequest req, ResourceContext resourceContext);
}
