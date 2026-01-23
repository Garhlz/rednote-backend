package logic

import (
	"context"
	"crypto/rand"
	"crypto/tls"
	"database/sql"
	"encoding/hex"
	"fmt"
	"math/big"
	"net/smtp"
	"strconv"
	"strings"
	"time"

	"user-rpc/internal/config"
	"user-rpc/internal/model"
	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
)

const (
	emailCodeKeyPrefix  = "verify:code:"
	emailLimitKeyPrefix = "verify:limit:"
	tokenBlockPrefix    = "auth:token:block:"
	refreshTokenPrefix  = "auth:refresh:"
	tokenVersionPrefix  = "auth:token:version:"
)

type tokenClaims struct {
	UserId       int64  `json:"userId"`
	Role         string `json:"role,omitempty"`
	Nickname     string `json:"nickname,omitempty"`
	Type         string `json:"type"`
	TokenVersion int64  `json:"tokenVersion"`
	jwt.RegisteredClaims
}

func buildUserSummary(u *model.Users) *user.UserSummary {
	return &user.UserSummary{
		UserId:   u.Id,
		Nickname: u.Nickname,
		Avatar:   u.Avatar,
		Role:     u.Role,
		Status:   int32(u.Status),
	}
}

func buildUserProfile(u *model.Users) *user.UserProfile {
	profile := &user.UserProfile{
		UserId:   u.Id,
		Email:    u.Email,
		Nickname: u.Nickname,
		Avatar:   u.Avatar,
		Bio:      u.Bio,
		Gender:   int32(u.Gender),
		Region:   u.Region.String,
		Role:     u.Role,
		Status:   int32(u.Status),
	}
	if u.Birthday.Valid {
		profile.Birthday = u.Birthday.Time.Format("2006-01-02")
	}
	if !u.Region.Valid {
		profile.Region = ""
	}
	return profile
}

func buildPublicProfile(u *model.Users) *user.PublicUserProfile {
	profile := &user.PublicUserProfile{
		UserId:   u.Id,
		Nickname: u.Nickname,
		Avatar:   u.Avatar,
		Bio:      u.Bio,
		Gender:   int32(u.Gender),
		Region:   u.Region.String,
	}
	if !u.Region.Valid {
		profile.Region = ""
	}
	return profile
}

func hashPassword(password string) (string, error) {
	hashed, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(hashed), nil
}

func checkPassword(hashed, password string) bool {
	if hashed == "" {
		return false
	}
	return bcrypt.CompareHashAndPassword([]byte(hashed), []byte(password)) == nil
}

func buildTokenPair(cfg config.Config, u *model.Users) (*user.TokenPair, string, error) {
	now := time.Now()
	accessExp := now.Add(time.Duration(cfg.Jwt.AccessExpireSeconds) * time.Second)
	refreshExp := now.Add(time.Duration(cfg.Jwt.RefreshExpireSeconds) * time.Second)
	refreshJti, err := newTokenJti()
	if err != nil {
		return nil, "", err
	}

	accessClaims := tokenClaims{
		UserId:       u.Id,
		Role:         u.Role,
		Nickname:     u.Nickname,
		Type:         "access",
		TokenVersion: u.TokenVersion,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    cfg.Jwt.Issuer,
			ExpiresAt: jwt.NewNumericDate(accessExp),
			IssuedAt:  jwt.NewNumericDate(now),
		},
	}

	refreshClaims := tokenClaims{
		UserId:       u.Id,
		Type:         "refresh",
		TokenVersion: u.TokenVersion,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    cfg.Jwt.Issuer,
			ID:        refreshJti,
			ExpiresAt: jwt.NewNumericDate(refreshExp),
			IssuedAt:  jwt.NewNumericDate(now),
		},
	}

	accessToken, err := jwt.NewWithClaims(jwt.SigningMethodHS256, accessClaims).SignedString([]byte(cfg.Jwt.Secret))
	if err != nil {
		return nil, "", err
	}

	refreshToken, err := jwt.NewWithClaims(jwt.SigningMethodHS256, refreshClaims).SignedString([]byte(cfg.Jwt.Secret))
	if err != nil {
		return nil, "", err
	}

	return &user.TokenPair{
		AccessToken:   accessToken,
		RefreshToken:  refreshToken,
		TokenType:     "Bearer",
		AccessExpire:  accessExp.Unix(),
		RefreshExpire: refreshExp.Unix(),
	}, refreshJti, nil
}

func parseToken(cfg config.Config, token string) (*tokenClaims, error) {
	claims := &tokenClaims{}
	_, err := jwt.ParseWithClaims(token, claims, func(t *jwt.Token) (any, error) {
		return []byte(cfg.Jwt.Secret), nil
	})
	if err != nil {
		return nil, err
	}
	return claims, nil
}

func parseBirthday(value string) (sql.NullTime, error) {
	if strings.TrimSpace(value) == "" {
		return sql.NullTime{Valid: false}, nil
	}
	parsed, err := time.Parse("2006-01-02", value)
	if err != nil {
		return sql.NullTime{}, err
	}
	return sql.NullTime{Time: parsed, Valid: true}, nil
}

func newTokenJti() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}

func refreshTokenKey(userId int64, jti string) string {
	return fmt.Sprintf("%s%d:%s", refreshTokenPrefix, userId, jti)
}

func tokenVersionKey(userId int64) string {
	return fmt.Sprintf("%s%d", tokenVersionPrefix, userId)
}

func setTokenVersion(ctx context.Context, svcCtx *svc.ServiceContext, userId int64, version int64) error {
	return svcCtx.Redis.SetCtx(ctx, tokenVersionKey(userId), strconv.FormatInt(version, 10))
}

func randCode() (string, error) {
	max := big.NewInt(1000000)
	num, err := rand.Int(rand.Reader, max)
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", num.Int64()), nil
}

func sendEmail(cfg config.Config, to string, subject string, body string) error {
	from := cfg.Mail.From
	if from == "" {
		from = cfg.Mail.User
	}
	addr := fmt.Sprintf("%s:%d", cfg.Mail.Host, cfg.Mail.Port)
	msg := buildEmailMessage(from, to, subject, body)
	auth := smtp.PlainAuth("", cfg.Mail.User, cfg.Mail.Pass, cfg.Mail.Host)

	if cfg.Mail.UseSSL {
		conn, err := tls.Dial("tcp", addr, &tls.Config{ServerName: cfg.Mail.Host})
		if err != nil {
			return err
		}
		defer conn.Close()

		client, err := smtp.NewClient(conn, cfg.Mail.Host)
		if err != nil {
			return err
		}
		defer client.Close()

		if cfg.Mail.User != "" {
			if err := client.Auth(auth); err != nil {
				return err
			}
		}
		if err := client.Mail(from); err != nil {
			return err
		}
		if err := client.Rcpt(to); err != nil {
			return err
		}
		writer, err := client.Data()
		if err != nil {
			return err
		}
		if _, err := writer.Write([]byte(msg)); err != nil {
			return err
		}
		if err := writer.Close(); err != nil {
			return err
		}
		return client.Quit()
	}

	return smtp.SendMail(addr, auth, from, []string{to}, []byte(msg))
}

func buildEmailMessage(from, to, subject, body string) string {
	lines := []string{
		fmt.Sprintf("From: %s", from),
		fmt.Sprintf("To: %s", to),
		fmt.Sprintf("Subject: %s", subject),
		"MIME-Version: 1.0",
		"Content-Type: text/plain; charset=UTF-8",
		"",
		body,
	}
	return strings.Join(lines, "\r\n")
}
