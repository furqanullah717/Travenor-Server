package com.codewithfk.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.InputStream

fun Application.configureSwagger() {
    routing {
        // Serve OpenAPI JSON spec
        get("/api-docs/openapi.json") {
            val openApiSpec = this::class.java.classLoader
                .getResourceAsStream("openapi.json")
                ?.bufferedReader()
                ?.readText()
                ?: throw IllegalStateException("openapi.json not found in resources")
            
            call.respondText(
                openApiSpec,
                ContentType.Application.Json
            )
        }
        
        // Serve Swagger UI
        get("/api-docs") {
            call.respondText(
                getSwaggerUiHtml(),
                ContentType.Text.Html
            )
        }
    }
}

private fun getSwaggerUiHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Trevnor Travel Marketplace API Documentation</title>
    <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5.10.3/swagger-ui.css" />
    <style>
        html {
            box-sizing: border-box;
            overflow: -moz-scrollbars-vertical;
            overflow-y: scroll;
        }
        *, *:before, *:after {
            box-sizing: inherit;
        }
        body {
            margin: 0;
            background: #fafafa;
        }
        .swagger-ui .topbar {
            background-color: #89bf04;
        }
        #loading {
            text-align: center;
            padding: 50px;
            font-family: sans-serif;
        }
    </style>
</head>
<body>
    <div id="swagger-ui">
        <div id="loading">
            <h2>Loading API Documentation...</h2>
            <p>If this message persists, check the browser console for errors.</p>
        </div>
    </div>
    <script src="https://unpkg.com/swagger-ui-dist@5.10.3/swagger-ui-bundle.js" crossorigin></script>
    <script src="https://unpkg.com/swagger-ui-dist@5.10.3/swagger-ui-standalone-preset.js" crossorigin></script>
    <script>
        (function() {
            function initSwagger() {
                try {
                    if (typeof SwaggerUIBundle === 'undefined') {
                        document.getElementById('loading').innerHTML = 
                            '<h2>Error: Swagger UI failed to load</h2>' +
                            '<p>Please check your internet connection or try refreshing the page.</p>';
                        return;
                    }
                    
                    const ui = SwaggerUIBundle({
                        url: "/api-docs/openapi.json",
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        presets: [
                            SwaggerUIBundle.presets.apis,
                            SwaggerUIStandalonePreset
                        ],
                        plugins: [
                            SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout",
                        tryItOutEnabled: true,
                        supportedSubmitMethods: ['get', 'post', 'put', 'delete', 'patch'],
                        validatorUrl: null,
                        onComplete: function() {
                            console.log("Swagger UI loaded successfully");
                        },
                        onFailure: function(data) {
                            console.error("Swagger UI failed to load:", data);
                            document.getElementById('swagger-ui').innerHTML = 
                                '<div style="padding: 20px; text-align: center;">' +
                                '<h2>Error loading API documentation</h2>' +
                                '<p>Failed to load OpenAPI specification.</p>' +
                                '<p>Please check: <a href="/api-docs/openapi.json" target="_blank">/api-docs/openapi.json</a></p>' +
                                '</div>';
                        }
                    });
                } catch (error) {
                    console.error("Error initializing Swagger UI:", error);
                    document.getElementById('swagger-ui').innerHTML = 
                        '<div style="padding: 20px; text-align: center;">' +
                        '<h2>Error initializing Swagger UI</h2>' +
                        '<p>' + error.message + '</p>' +
                        '</div>';
                }
            }
            
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', initSwagger);
            } else {
                initSwagger();
            }
        })();
    </script>
</body>
</html>
    """.trimIndent()
}
