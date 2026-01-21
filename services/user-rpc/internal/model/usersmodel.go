package model

import (
	"context"
	"fmt"
	"strings"

	"github.com/zeromicro/go-zero/core/stores/sqlx"
)

var _ UsersModel = (*customUsersModel)(nil)

type (
	// UsersModel is an interface to be customized, add more methods here,
	// and implement the added methods in customUsersModel.
	UsersModel interface {
		usersModel
		FindByIds(ctx context.Context, ids []int64) ([]*Users, error)
		withSession(session sqlx.Session) UsersModel
	}

	customUsersModel struct {
		*defaultUsersModel
	}
)

// NewUsersModel returns a model for the database table.
func NewUsersModel(conn sqlx.SqlConn) UsersModel {
	return &customUsersModel{
		defaultUsersModel: newUsersModel(conn),
	}
}

func (m *customUsersModel) withSession(session sqlx.Session) UsersModel {
	return NewUsersModel(sqlx.NewSqlConnFromSession(session))
}

func (m *customUsersModel) FindByIds(ctx context.Context, ids []int64) ([]*Users, error) {
	if len(ids) == 0 {
		return []*Users{}, nil
	}

	placeholders := make([]string, 0, len(ids))
	args := make([]any, 0, len(ids))
	for i, id := range ids {
		placeholders = append(placeholders, fmt.Sprintf("$%d", i+1))
		args = append(args, id)
	}

	query := fmt.Sprintf("select %s from %s where id in (%s)", usersRows, m.table, strings.Join(placeholders, ","))
	var resp []*Users
	err := m.conn.QueryRowsCtx(ctx, &resp, query, args...)
	return resp, err
}
