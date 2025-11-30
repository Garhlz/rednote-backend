import bcrypt
print(bcrypt.hashpw(b'123456', bcrypt.gensalt()).decode())