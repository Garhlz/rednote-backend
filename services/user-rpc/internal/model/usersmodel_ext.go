package model

import (
	"context"
	"fmt"
	"strings"

	"github.com/zeromicro/go-zero/core/stores/sqlx"
)

var _ UsersExtendedModel = (*customUsersModel)(nil)

type UsersExtendedModel interface {
	UsersModel
	FindByIds(ctx context.Context, ids []int64) ([]*Users, error)
	InsertAndReturnID(ctx context.Context, data *Users) (int64, error)
}

func NewUsersExtendedModel(conn sqlx.SqlConn) UsersExtendedModel {
	return &customUsersModel{
		defaultUsersModel: newUsersModel(conn),
	}
}

func (m *customUsersModel) FindByIds(ctx context.Context, ids []int64) ([]*Users, error) {
	if len(ids) == 0 {
		return []*Users{}, nil
	}

	placeholders := make([]string, 0, len(ids))
	args := make([]any, 0, len(ids))
	for range ids {
		placeholders = append(placeholders, "?")
	}
	for _, id := range ids {
		args = append(args, id)
	}

	query := fmt.Sprintf("select %s from %s where `id` in (%s)", usersRows, m.table, strings.Join(placeholders, ","))
	var resp []*Users
	err := m.conn.QueryRowsCtx(ctx, &resp, query, args...)
	return resp, err
}

func (m *customUsersModel) InsertAndReturnID(ctx context.Context, data *Users) (int64, error) {
	ret, err := m.defaultUsersModel.Insert(ctx, data)
	if err != nil {
		return 0, err
	}

	id, err := ret.LastInsertId()
	if err != nil {
		return 0, err
	}

	return id, nil
}
