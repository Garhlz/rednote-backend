package obslog

import (
	"fmt"
	"log"

	amqp "github.com/rabbitmq/amqp091-go"
)

const serviceName = "sync-sidecar"

func Infof(format string, args ...any) {
	log.Printf(prefix()+" "+format, args...)
}

func Errorf(format string, args ...any) {
	log.Printf(prefix()+" "+format, args...)
}

func DeliveryInfof(d amqp.Delivery, format string, args ...any) {
	log.Printf(prefixForDelivery(d)+" "+format, args...)
}

func DeliveryErrorf(d amqp.Delivery, format string, args ...any) {
	log.Printf(prefixForDelivery(d)+" "+format, args...)
}

func prefix() string {
	return fmt.Sprintf("service=%s", serviceName)
}

func prefixForDelivery(d amqp.Delivery) string {
	requestID := headerValue(d.Headers, "X-Request-Id")
	if requestID == "" {
		requestID = headerValue(d.Headers, "X-Trace-Id")
	}
	return fmt.Sprintf("service=%s requestId=%s traceId=%s routingKey=%s", serviceName, requestID, requestID, d.RoutingKey)
}

func headerValue(headers amqp.Table, key string) string {
	if headers == nil {
		return ""
	}
	if value, ok := headers[key]; ok && value != nil {
		return fmt.Sprint(value)
	}
	return ""
}
