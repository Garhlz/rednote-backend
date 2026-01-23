package svc

import (
	"bytes"
	"context"
	"io"
	"net/http"
	"net/url"
	"strings"
)

type JavaProxyRequest struct {
	Method  string
	Path    string
	Query   url.Values
	Headers map[string]string
	Body    []byte
}

type JavaProxyResponse struct {
	StatusCode int
	Body       []byte
	Headers    http.Header
}

func (s *ServiceContext) ProxyToJava(ctx context.Context, req JavaProxyRequest) (*JavaProxyResponse, error) {
	base := strings.TrimRight(s.JavaApiBaseUrl, "/")
	path := strings.TrimLeft(req.Path, "/")
	fullUrl := base + "/" + path
	if len(req.Query) > 0 {
		fullUrl = fullUrl + "?" + req.Query.Encode()
	}

	body := bytes.NewReader(req.Body)
	httpReq, err := http.NewRequestWithContext(ctx, req.Method, fullUrl, body)
	if err != nil {
		return nil, err
	}

	for k, v := range req.Headers {
		httpReq.Header.Set(k, v)
	}

	resp, err := s.JavaHttpClient.Do(httpReq)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	return &JavaProxyResponse{
		StatusCode: resp.StatusCode,
		Body:       respBody,
		Headers:    resp.Header,
	}, nil
}
