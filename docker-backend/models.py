# -*- coding: utf-8 -*-
"""
记工App - 数据模型层
===================

数据库表结构:
1. users - 用户表
2. work_records - 记工记录表
3. settings - 系统设置表
4. backups - 备份记录表

核心函数:
- 用户认证（注册、登录、密码验证）
- 数据库初始化与连接
- 记录CRUD操作
- 统计计算
- 备份管理
- 工资计算与数据对比
- 日历视图与年度统计
- 批量导入导出

作者: 小扣子
版本: 1.19.0
"""

import sqlite3
import os
import hashlib
import secrets
from datetime import datetime

# ============================================================================
# 数据库配置
# ============================================================================

# 优先使用环境变量，支持Docker部署
# 本地开发时数据库存放在 data/ 目录
DATABASE = os.environ.get(
    'DATABASE_PATH', 
    os.path.join(os.path.dirname(__file__), 'data', 'work_records.db')
)

# 确保数据目录存在
db_dir = os.path.dirname(DATABASE)
if db_dir and not os.path.exists(db_dir):
    os.makedirs(db_dir, exist_ok=True)


# ============================================================================
# 数据库连接管理
# ============================================================================

def get_db():
    """
    获取数据库连接
    
    使用 sqlite3.Row 作为行工厂，支持通过列名访问数据
    
    返回:
        sqlite3.Connection: 数据库连接对象
    
    注意:
        调用者负责关闭连接
    """
    conn = sqlite3.connect(DATABASE)
    conn.row_factory = sqlite3.Row
    return conn


def dict_from_row(row):
    """
    将 sqlite3.Row 对象转换为字典
    
    参数:
        row: sqlite3.Row 对象
    
    返回:
        dict: 键值对字典，如果row为None则返回None
    """
    if row is None:
        return None
    return dict(zip(row.keys(), row))


# ============================================================================
# 密码加密
# ============================================================================

def hash_password(password, salt=None):
    """
    使用SHA256加密密码
    
    参数:
        password: 明文密码
        salt: 盐值（可选，不传则生成新盐值）
    
    返回:
        tuple: (加密后的密码, 盐值)
    """
    if salt is None:
        salt = secrets.token_hex(16)
    
    # 密码 + 盐值 进行SHA256加密
    hashed = hashlib.sha256((password + salt).encode()).hexdigest()
    return hashed, salt


def verify_password(password, hashed_password, salt):
    """
    验证密码
    
    参数:
        password: 明文密码
        hashed_password: 存储的加密密码
        salt: 盐值
    
    返回:
        bool: 密码是否正确
    """
    new_hash, _ = hash_password(password, salt)
    return new_hash == hashed_password


# ============================================================================
# 数据库初始化
# ============================================================================

def init_db():
    """
    初始化数据库
    
    创建以下表:
    1. users - 用户表
       - id: 主键
       - username: 用户名（唯一）
       - password: 加密密码
       - salt: 密码盐值
       - created_at: 创建时间
    
    2. work_records - 记工记录表
       - id: 主键
       - user_id: 用户ID（外键）
       - record_type: 记录类型 (standard/manual/overtime)
       - work_date: 工作日期
       - location: 工作地点
       - start_time: 开始时间
       - end_time: 结束时间
       - created_at: 创建时间
       - updated_at: 更新时间
    
    3. settings - 系统设置表
       - user_id: 用户ID（外键）
       - key: 设置项名称
       - value: 设置值
    
    4. backups - 备份记录表
       - id: 主键
       - user_id: 用户ID（外键）
       - filename: 备份文件名
       - backup_date: 备份时间
       - data_hash: 数据哈希
    """
    conn = get_db()
    cursor = conn.cursor()
    
    # 创建用户表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            salt TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    
    # 创建记工记录表（添加user_id和软删除字段）
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS work_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            record_type TEXT NOT NULL,
            work_date DATE NOT NULL,
            location TEXT NOT NULL,
            start_time TEXT,
            end_time TEXT,
            morning_end_time TEXT,
            afternoon_start_time TEXT,
            hours REAL,
            deleted_at TIMESTAMP DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
    ''')
    
    # 为已存在的表添加 deleted_at 字段（兼容旧数据）
    try:
        cursor.execute('ALTER TABLE work_records ADD COLUMN deleted_at TIMESTAMP DEFAULT NULL')
    except:
        pass  # 字段已存在
    
    # 为已存在的表添加中午休息时间字段
    try:
        cursor.execute('ALTER TABLE work_records ADD COLUMN morning_end_time TEXT')
    except:
        pass  # 字段已存在
    try:
        cursor.execute('ALTER TABLE work_records ADD COLUMN afternoon_start_time TEXT')
    except:
        pass  # 字段已存在
    
    # 创建设置表（添加user_id）
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS settings (
            user_id INTEGER NOT NULL,
            key TEXT NOT NULL,
            value TEXT NOT NULL,
            PRIMARY KEY (user_id, key),
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
    ''')
    
    # 添加v1.13.0新设置字段
    new_settings = [
        ('enable_missed_reminder', '1'),
        ('reminder_exclude_weekend', '1'),
        ('enable_hours_check', '1'),
        ('enable_offwork_reminder', '0'),
        ('offwork_time', '18:00'),
        ('sync_wifi_only', '0'),
        ('theme', 'auto'),
        ('quick_phrases', '["调休","出差","请假","事假","病假"]')
    ]
    for key, value in new_settings:
        try:
            cursor.execute('''
                INSERT INTO settings (user_id, key, value) VALUES (1, ?, ?)
            ''', (key, value))
        except:
            pass  # 字段或默认值已存在
    
    # 创建云盘配置表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS cloud_config (
            user_id INTEGER NOT NULL,
            provider TEXT NOT NULL,
            access_token TEXT,
            refresh_token TEXT,
            expires_at TIMESTAMP,
            config_data TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id, provider),
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
    ''')
    
    # 创建备份记录表（添加user_id）
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS backups (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            filename TEXT NOT NULL,
            backup_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            data_hash TEXT,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
    ''')
    
    conn.commit()
    
    # 创建默认管理员用户（如果不存在）
    cursor.execute('SELECT id FROM users WHERE username = ?', ('admin',))
    if not cursor.fetchone():
        # 创建默认admin用户，密码为 admin123
        hashed_password, salt = hash_password('admin123')
        cursor.execute(
            'INSERT INTO users (username, password, salt) VALUES (?, ?, ?)',
            ('admin', hashed_password, salt)
        )
        conn.commit()
    
    conn.close()


# ============================================================================
# 用户管理
# ============================================================================

def create_user(username, password):
    """
    创建新用户
    
    参数:
        username: 用户名
        password: 明文密码
    
    返回:
        tuple: (success: bool, message: str, user_id: int or None)
    """
    conn = get_db()
    cursor = conn.cursor()
    
    # 检查用户名是否已存在
    cursor.execute('SELECT id FROM users WHERE username = ?', (username,))
    if cursor.fetchone():
        conn.close()
        return False, '用户名已存在', None
    
    # 加密密码
    hashed_password, salt = hash_password(password)
    
    # 插入用户
    try:
        cursor.execute('''
            INSERT INTO users (username, password, salt) VALUES (?, ?, ?)
        ''', (username, hashed_password, salt))
        user_id = cursor.lastrowid
        
        # 为新用户初始化默认设置
        default_settings = {
            'daily_hours': '8',
            'overtime_rate': '8',
            'meal_subsidy': '15',
            'backup_interval': '7',
            'backup_count': '3',
            'font_size': '16',
            'theme': 'auto',
            'trash_retention': '30',
            'daily_wage': '0',
            'monthly_hours_target': '0',
            # v1.13.0新增设置
            'enable_missed_reminder': '1',
            'reminder_exclude_weekend': '1',
            'enable_hours_check': '1',
            'enable_offwork_reminder': '0',
            'offwork_time': '18:00',
            'sync_wifi_only': '0',
            'quick_phrases': '["调休","出差","请假","事假","病假"]'
        }
        
        for key, value in default_settings.items():
            cursor.execute('''
                INSERT OR IGNORE INTO settings (user_id, key, value) VALUES (?, ?, ?)
            ''', (user_id, key, value))
        
        conn.commit()
        conn.close()
        return True, '注册成功', user_id
    except Exception as e:
        conn.rollback()
        conn.close()
        return False, str(e), None


def authenticate_user(username, password):
    """
    验证用户登录
    
    参数:
        username: 用户名
        password: 明文密码
    
    返回:
        tuple: (success: bool, message: str, user: dict or None)
    """
    conn = get_db()
    cursor = conn.cursor()
    
    cursor.execute('SELECT * FROM users WHERE username = ?', (username,))
    user = cursor.fetchone()
    conn.close()
    
    if not user:
        return False, '用户名不存在', None
    
    if verify_password(password, user['password'], user['salt']):
        return True, '登录成功', dict_from_row(user)
    else:
        return False, '密码错误', None


def get_user_by_id(user_id):
    """
    根据ID获取用户信息
    
    参数:
        user_id: 用户ID
    
    返回:
        dict: 用户信息字典
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT id, username, created_at FROM users WHERE id = ?', (user_id,))
    user = cursor.fetchone()
    conn.close()
    return dict_from_row(user)


# ============================================================================
# 设置管理（需要user_id）
# ============================================================================

def get_all_settings(user_id):
    """
    获取指定用户的所有设置
    
    参数:
        user_id: 用户ID
    
    返回:
        dict: 设置键值对字典
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('SELECT key, value FROM settings WHERE user_id = ?', (user_id,))
    rows = cursor.fetchall()
    conn.close()
    return {row['key']: row['value'] for row in rows}


def update_setting(user_id, key, value):
    """
    更新指定用户的单个设置
    
    参数:
        user_id: 用户ID
        key: 设置项名称
        value: 设置值
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        INSERT OR REPLACE INTO settings (user_id, key, value) VALUES (?, ?, ?)
    ''', (user_id, key, str(value)))
    conn.commit()
    conn.close()


def update_settings(user_id, settings_dict):
    """
    批量更新指定用户的设置
    
    参数:
        user_id: 用户ID
        settings_dict: 设置键值对字典
    """
    conn = get_db()
    cursor = conn.cursor()
    for key, value in settings_dict.items():
        cursor.execute('''
            INSERT OR REPLACE INTO settings (user_id, key, value) VALUES (?, ?, ?)
        ''', (user_id, key, str(value)))
    conn.commit()
    conn.close()


# ============================================================================
# 记工记录管理（需要user_id）
# ============================================================================

def add_work_record(user_id, record_type, work_date, location, start_time=None, end_time=None, 
                    morning_end_time=None, afternoon_start_time=None, hours=None):
    """
    添加记工记录
    
    参数:
        user_id: 用户ID
        record_type: 记录类型 (standard/manual/overtime)
        work_date: 工作日期 (YYYY-MM-DD)
        location: 工作地点（必填）
        start_time: 开始时间 (HH:MM)
        end_time: 结束时间 (HH:MM)
        morning_end_time: 上午下班时间 (HH:MM)
        afternoon_start_time: 下午上班时间 (HH:MM)
        hours: 工作时长（小时）
    
    返回:
        int: 新记录的ID
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        INSERT INTO work_records (user_id, record_type, work_date, location, start_time, end_time, 
                                  morning_end_time, afternoon_start_time, hours)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', (user_id, record_type, work_date, location, start_time, end_time, 
          morning_end_time, afternoon_start_time, hours))
    record_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return record_id


def update_work_record(user_id, record_id, record_type, work_date, location, start_time=None, end_time=None,
                       morning_end_time=None, afternoon_start_time=None, hours=None):
    """
    更新记工记录
    
    参数:
        user_id: 用户ID（用于权限验证）
        record_id: 记录ID
        其他参数同 add_work_record
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        UPDATE work_records 
        SET record_type=?, work_date=?, location=?, start_time=?, end_time=?, 
            morning_end_time=?, afternoon_start_time=?, hours=?, updated_at=CURRENT_TIMESTAMP
        WHERE id=? AND user_id=?
    ''', (record_type, work_date, location, start_time, end_time, 
          morning_end_time, afternoon_start_time, hours, record_id, user_id))
    conn.commit()
    conn.close()


def get_work_record_by_id(user_id, record_id):
    """
    根据ID获取单条记录
    
    参数:
        user_id: 用户ID
        record_id: 记录ID
    
    返回:
        dict: 记录字典，不存在则返回None
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT * FROM work_records 
        WHERE id=? AND user_id=? AND deleted_at IS NULL
    ''', (record_id, user_id))
    row = cursor.fetchone()
    conn.close()
    return dict_from_row(row)


def delete_related_overtime(user_id, work_date, location):
    """
    删除同一天同地点的关联加班记录
    
    参数:
        user_id: 用户ID
        work_date: 工作日期
        location: 工作地点
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        UPDATE work_records 
        SET deleted_at = CURRENT_TIMESTAMP 
        WHERE user_id=? AND work_date=? AND location=? 
        AND record_type='overtime' AND deleted_at IS NULL
    ''', (user_id, work_date, location))
    conn.commit()
    conn.close()


def delete_work_record(user_id, record_id):
    """
    软删除记工记录（移入回收站）
    
    参数:
        user_id: 用户ID（用于权限验证）
        record_id: 记录ID
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        UPDATE work_records 
        SET deleted_at = CURRENT_TIMESTAMP 
        WHERE id=? AND user_id=? AND deleted_at IS NULL
    ''', (record_id, user_id))
    conn.commit()
    conn.close()


def get_work_records(user_id, limit=50):
    """
    获取指定用户最近的记工记录（排除已删除）
    
    参数:
        user_id: 用户ID
        limit: 返回记录数量，默认50
    
    返回:
        list: 记录字典列表，按日期倒序排列
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT * FROM work_records 
        WHERE user_id = ? AND deleted_at IS NULL
        ORDER BY work_date DESC, created_at DESC 
        LIMIT ?
    ''', (user_id, limit))
    rows = cursor.fetchall()
    conn.close()
    return [dict_from_row(row) for row in rows]


def get_records_by_date_range(user_id, start_date, end_date):
    """
    按日期范围获取指定用户的记录（排除已删除）
    
    参数:
        user_id: 用户ID
        start_date: 开始日期 (YYYY-MM-DD)
        end_date: 结束日期 (YYYY-MM-DD)
    
    返回:
        list: 记录字典列表
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT * FROM work_records 
        WHERE user_id = ? AND deleted_at IS NULL AND work_date >= ? AND work_date <= ?
        ORDER BY work_date DESC, created_at DESC
    ''', (user_id, start_date, end_date))
    rows = cursor.fetchall()
    conn.close()
    return [dict_from_row(row) for row in rows]


def get_records_by_year(user_id, year):
    """
    获取指定用户指定年份的所有记录（排除已删除）
    
    参数:
        user_id: 用户ID
        year: 年份（整数）
    
    返回:
        list: 记录字典列表
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT * FROM work_records 
        WHERE user_id = ? AND deleted_at IS NULL AND strftime('%Y', work_date) = ?
        ORDER BY work_date DESC
    ''', (user_id, str(year)))
    rows = cursor.fetchall()
    conn.close()
    return [dict_from_row(row) for row in rows]


def get_records_by_month(user_id, year, month):
    """
    获取指定用户指定月份的所有记录（排除已删除）
    
    参数:
        user_id: 用户ID
        year: 年份（整数）
        month: 月份（整数，1-12）
    
    返回:
        list: 记录字典列表
    """
    conn = get_db()
    cursor = conn.cursor()
    month_str = f"{year}-{month:02d}"
    cursor.execute('''
        SELECT * FROM work_records 
        WHERE user_id = ? AND deleted_at IS NULL AND work_date LIKE ?
        ORDER BY work_date DESC
    ''', (user_id, f"{month_str}%"))
    rows = cursor.fetchall()
    conn.close()
    return [dict_from_row(row) for row in rows]


def check_duplicate_record(user_id, work_date, record_type=None):
    """
    检查指定日期是否已有记工记录
    
    参数:
        user_id: 用户ID
        work_date: 工作日期 (YYYY-MM-DD)
        record_type: 记录类型（可选，用于更精确检测）
    
    返回:
        dict: 包含是否有重复记录和记录详情的字典
    """
    conn = get_db()
    cursor = conn.cursor()
    
    if record_type:
        cursor.execute('''
            SELECT * FROM work_records 
            WHERE user_id = ? AND work_date = ? AND record_type = ? AND deleted_at IS NULL
        ''', (user_id, work_date, record_type))
    else:
        cursor.execute('''
            SELECT * FROM work_records 
            WHERE user_id = ? AND work_date = ? AND deleted_at IS NULL
        ''', (user_id, work_date))
    
    rows = cursor.fetchall()
    conn.close()
    
    if rows:
        return {
            'has_duplicate': True,
            'records': [dict_from_row(row) for row in rows]
        }
    return {
        'has_duplicate': False,
        'records': []
    }


# ============================================================================
# 回收站管理
# ============================================================================

def get_trash_records(user_id, limit=50):
    """
    获取回收站中的记录
    
    参数:
        user_id: 用户ID
        limit: 返回记录数量
    
    返回:
        list: 已删除的记录列表
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT * FROM work_records 
        WHERE user_id = ? AND deleted_at IS NOT NULL
        ORDER BY deleted_at DESC 
        LIMIT ?
    ''', (user_id, limit))
    rows = cursor.fetchall()
    conn.close()
    return [dict_from_row(row) for row in rows]


def restore_work_record(user_id, record_id):
    """
    恢复已删除的记录
    
    参数:
        user_id: 用户ID
        record_id: 记录ID
    
    返回:
        bool: 是否恢复成功
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        UPDATE work_records 
        SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP
        WHERE id=? AND user_id=? AND deleted_at IS NOT NULL
    ''', (record_id, user_id))
    success = cursor.rowcount > 0
    conn.commit()
    conn.close()
    return success


def permanent_delete_work_record(user_id, record_id):
    """
    永久删除记录（从数据库中彻底删除）
    
    参数:
        user_id: 用户ID
        record_id: 记录ID
    
    返回:
        bool: 是否删除成功
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        DELETE FROM work_records 
        WHERE id=? AND user_id=? AND deleted_at IS NOT NULL
    ''', (record_id, user_id))
    success = cursor.rowcount > 0
    conn.commit()
    conn.close()
    return success


def clean_expired_trash(user_id, retention_days=30):
    """
    清理过期的回收站数据
    
    参数:
        user_id: 用户ID
        retention_days: 保留天数
    
    返回:
        int: 清理的记录数量
    """
    conn = get_db()
    cursor = conn.cursor()
    
    # 删除超过保留天数的记录
    cursor.execute('''
        DELETE FROM work_records 
        WHERE user_id = ? 
        AND deleted_at IS NOT NULL 
        AND date(deleted_at) < date('now', ?)
    ''', (user_id, f'-{retention_days} days'))
    
    deleted_count = cursor.rowcount
    conn.commit()
    conn.close()
    return deleted_count


def empty_trash(user_id):
    """
    清空回收站
    
    参数:
        user_id: 用户ID
    
    返回:
        int: 清理的记录数量
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        DELETE FROM work_records 
        WHERE user_id = ? AND deleted_at IS NOT NULL
    ''', (user_id,))
    deleted_count = cursor.rowcount
    conn.commit()
    conn.close()
    return deleted_count


# ============================================================================
# 统计计算（需要user_id）
# ============================================================================

def calculate_overtime_distribution(records, overtime_rate):
    """
    计算加班分布统计（按加班天数分类）
    
    参数:
        records: 记录列表
        overtime_rate: 加班折算率（每天多少小时）
    
    返回:
        list: 加班分布列表，按加班天数从少到多排序
    """
    if not overtime_rate or overtime_rate <= 0:
        overtime_rate = 8  # 默认每天8小时
    
    # 按天分组统计每天的加班小时数
    daily_overtime = {}  # {date: hours}
    
    for record in records:
        if record['record_type'] == 'overtime':
            date = record.get('work_date')
            hours = record.get('hours') or 0
            if date:
                if date not in daily_overtime:
                    daily_overtime[date] = 0
                daily_overtime[date] += hours
    
    # 将加班小时数转换为加班天数并分类统计
    overtime_days_count = {}  # {days_level: {'days': count, 'hours': total_hours}}
    
    for date, hours in daily_overtime.items():
        # 计算加班天数（精确到0.5天，即4小时）
        overtime_days = hours / overtime_rate
        # 四舍五入到0.5的倍数
        overtime_days = round(overtime_days * 2) / 2
        
        if overtime_days not in overtime_days_count:
            overtime_days_count[overtime_days] = {'days': 0, 'hours': 0}
        overtime_days_count[overtime_days]['days'] += 1
        overtime_days_count[overtime_days]['hours'] += hours
    
    # 构建分布列表
    distribution = []
    
    # 无加班的情况
    total_standard_days = 0
    for record in records:
        if record['record_type'] == 'standard':
            total_standard_days += 1
    
    # 统计有加班的天数
    overtime_days_total = sum(v['days'] for k, v in overtime_days_count.items() if k > 0)
    no_overtime_days = total_standard_days - overtime_days_total
    
    if no_overtime_days > 0:
        distribution.append({
            'label': '无加班',
            'days': no_overtime_days,
            'hours': 0
        })
    
    # 按加班天数从少到多排序
    sorted_keys = sorted([k for k in overtime_days_count.keys() if k > 0])
    
    for overtime_days in sorted_keys:
        data = overtime_days_count[overtime_days]
        if overtime_days == 0.5:
            label = '加班0.5天'
        elif overtime_days == int(overtime_days):
            label = f'加班{int(overtime_days)}天'
        else:
            label = f'加班{overtime_days}天'
        
        distribution.append({
            'label': label,
            'days': data['days'],
            'hours': round(data['hours'], 1)
        })
    
    # 计算总计
    total_overtime_days = sum(d['days'] for d in distribution if d['label'] != '无加班')
    total_overtime_hours = sum(d['hours'] for d in distribution if d['label'] != '无加班')
    
    # 添加总计行
    if distribution:
        distribution.append({
            'label': '总计',
            'days': total_overtime_days,
            'hours': round(total_overtime_hours, 1),
            'is_total': True
        })
    
    return distribution


def get_available_months(user_id):
    """
    获取用户有记录的所有月份列表
    
    参数:
        user_id: 用户ID
    
    返回:
        list: 月份列表，每项包含 {year, month, label}，按时间倒序排列
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT DISTINCT 
            CAST(SUBSTR(work_date, 1, 4) AS INTEGER) as year,
            CAST(SUBSTR(work_date, 6, 2) AS INTEGER) as month
        FROM work_records 
        WHERE user_id = ? AND deleted_at IS NULL
        ORDER BY year DESC, month DESC
    ''', (user_id,))
    rows = cursor.fetchall()
    conn.close()
    
    months = []
    for row in rows:
        year = row['year']
        month = row['month']
        months.append({
            'year': year,
            'month': month,
            'label': f"{year}年{month}月"
        })
    
    return months


def get_statistics(user_id, start_date=None, end_date=None, year=None, month=None):
    """
    获取指定用户的统计数据
    
    参数:
        user_id: 用户ID
        其他参数: 日期范围筛选
    
    返回:
        dict: 统计结果字典
    """
    # 获取用户设置
    settings = get_all_settings(user_id)
    daily_hours = float(settings.get('daily_hours', 8))
    overtime_rate = float(settings.get('overtime_rate', 8))
    meal_subsidy = float(settings.get('meal_subsidy', 15))
    daily_wage = float(settings.get('daily_wage', 0))
    
    # 获取记录
    if year:
        records = get_records_by_year(user_id, year)
    elif month:
        records = get_records_by_month(user_id, month.get('year'), month.get('month'))
    elif start_date and end_date:
        records = get_records_by_date_range(user_id, start_date, end_date)
    else:
        records = get_work_records(user_id, limit=10000)
    
    # 统计计算
    standard_count = 0
    overtime_hours = 0
    manual_hours = 0
    location_stats = {}
    
    for record in records:
        hours = record.get('hours') or 0
        location = record.get('location', '未知地点')
        
        if location not in location_stats:
            location_stats[location] = {'days': 0, 'hours': 0, 'standard': 0, 'overtime': 0, 'manual': 0}
        
        if record['record_type'] == 'standard':
            standard_count += 1
            location_stats[location]['days'] += 1
            location_stats[location]['hours'] += hours
            location_stats[location]['standard'] += 1
        elif record['record_type'] == 'overtime':
            overtime_hours += hours
            location_stats[location]['hours'] += hours
            location_stats[location]['overtime'] += hours
        elif record['record_type'] == 'manual':
            manual_hours += hours
            location_stats[location]['hours'] += hours
            location_stats[location]['manual'] += hours
    
    # 计算折算
    overtime_standard = overtime_hours / overtime_rate if overtime_rate > 0 else 0
    manual_standard = manual_hours / daily_hours if daily_hours > 0 else 0
    total_standard = standard_count + overtime_standard + manual_standard
    # 饭补计算：标准工全额 + 手动记工按工时比例，加班无饭补
    meal_subsidy_total = (standard_count * meal_subsidy) + (manual_hours / daily_hours * meal_subsidy) if daily_hours > 0 else standard_count * meal_subsidy
    
    # 计算应发工资
    # 月度应发工资 = 总标准工 × 日工资
    total_wage = total_standard * daily_wage
    
    # 为每个地点计算折算工数
    for loc in location_stats:
        loc_data = location_stats[loc]
        # 标准工数量
        loc_data['standard_count'] = loc_data['standard']
        # 手动折算工数
        loc_data['manual_standard'] = loc_data['manual'] / daily_hours if daily_hours > 0 else 0
        # 加班折算工数
        loc_data['overtime_standard'] = loc_data['overtime'] / overtime_rate if overtime_rate > 0 else 0
        # 总折算工数
        loc_data['standard_equivalent'] = (
            loc_data['standard'] + 
            loc_data['manual_standard'] +
            loc_data['overtime_standard']
        )
    
    # 计算加班分布统计（按加班天数分类）
    overtime_distribution = calculate_overtime_distribution(records, overtime_rate)
    
    return {
        'standard_count': standard_count,
        'overtime_hours': round(overtime_hours, 2),
        'overtime_standard': round(overtime_standard, 2),
        'manual_hours': round(manual_hours, 2),
        'manual_standard': round(manual_standard, 2),
        'total_standard': round(total_standard, 2),
        'meal_subsidy': round(meal_subsidy_total, 2),
        'daily_wage': daily_wage,
        'total_wage': round(total_wage, 2),
        'location_stats': location_stats,
        'daily_hours': daily_hours,
        'overtime_rate': overtime_rate,
        'overtime_distribution': overtime_distribution
    }


def get_monthly_comparison(user_id):
    """
    获取月度对比数据（本月 vs 上月 vs 去年同月）
    
    参数:
        user_id: 用户ID
    
    返回:
        dict: 包含本月、上月、去年同月统计数据及对比的字典
    """
    now = datetime.now()
    current_year = now.year
    current_month = now.month
    
    # 本月
    this_month_stats = get_statistics(user_id, month={'year': current_year, 'month': current_month})
    
    # 上月
    if current_month == 1:
        last_month_year = current_year - 1
        last_month = 12
    else:
        last_month_year = current_year
        last_month = current_month - 1
    last_month_stats = get_statistics(user_id, month={'year': last_month_year, 'month': last_month})
    
    # 去年同月
    same_month_last_year = get_statistics(user_id, month={'year': current_year - 1, 'month': current_month})
    
    # 计算对比
    def calc_change(current, previous):
        """计算变化百分比"""
        if previous == 0:
            return 100.0 if current > 0 else 0.0
        return round(((current - previous) / previous) * 100, 1)
    
    return {
        'this_month': {
            'year': current_year,
            'month': current_month,
            'stats': this_month_stats
        },
        'last_month': {
            'year': last_month_year,
            'month': last_month,
            'stats': last_month_stats
        },
        'same_month_last_year': {
            'year': current_year - 1,
            'month': current_month,
            'stats': same_month_last_year
        },
        'comparison': {
            'hours_change': calc_change(this_month_stats['total_standard'], last_month_stats['total_standard']),
            'meal_change': calc_change(this_month_stats['meal_subsidy'], last_month_stats['meal_subsidy']),
            'wage_change': calc_change(this_month_stats['total_wage'], last_month_stats['total_wage']),
            'hours_yoy_change': calc_change(this_month_stats['total_standard'], same_month_last_year['total_standard']),
            'meal_yoy_change': calc_change(this_month_stats['meal_subsidy'], same_month_last_year['meal_subsidy']),
            'wage_yoy_change': calc_change(this_month_stats['total_wage'], same_month_last_year['total_wage'])
        }
    }


def get_monthly_trend(user_id, months=6):
    """
    获取近N个月的工时趋势数据
    
    参数:
        user_id: 用户ID
        months: 返回的月数，默认6
    
    返回:
        list: 每月统计数据列表
    """
    now = datetime.now()
    trend_data = []
    
    for i in range(months - 1, -1, -1):
        # 计算目标月份
        target_month = now.month - i
        target_year = now.year
        
        while target_month <= 0:
            target_month += 12
            target_year -= 1
        
        stats = get_statistics(user_id, month={'year': target_year, 'month': target_month})
        trend_data.append({
            'year': target_year,
            'month': target_month,
            'label': f"{target_year}-{target_month:02d}",
            'total_standard': stats['total_standard'],
            'standard_count': stats['standard_count'],
            'overtime_hours': stats['overtime_hours']
        })
    
    return trend_data


def get_current_month_progress(user_id):
    """
    获取当月工时进度
    
    参数:
        user_id: 用户ID
    
    返回:
        dict: 包含当前工时和目标进度的字典
    """
    now = datetime.now()
    settings = get_all_settings(user_id)
    target = float(settings.get('monthly_hours_target', 0))
    
    # 获取当月数据
    current_stats = get_statistics(user_id, month={'year': now.year, 'month': now.month})
    
    return {
        'current_hours': current_stats['total_standard'],
        'target_hours': target,
        'progress': round((current_stats['total_standard'] / target * 100), 1) if target > 0 else 0,
        'is_complete': target > 0 and current_stats['total_standard'] >= target,
        'is_over': target > 0 and current_stats['total_standard'] >= target * 1.1
    }


# ============================================================================
# 数据清理（需要user_id）
# ============================================================================

def clear_all_records(user_id):
    """
    清空指定用户的所有记工记录
    
    参数:
        user_id: 用户ID
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('DELETE FROM work_records WHERE user_id = ?', (user_id,))
    conn.commit()
    conn.close()


# ============================================================================
# 备份管理（需要user_id）
# ============================================================================

def add_backup_record(user_id, filename, data_hash):
    """
    添加备份记录并清理旧备份
    
    参数:
        user_id: 用户ID
        filename: 备份文件名
        data_hash: 数据哈希值
    
    返回:
        list: 被删除的旧备份文件名列表
    """
    conn = get_db()
    cursor = conn.cursor()
    
    # 获取保留份数设置
    cursor.execute('SELECT value FROM settings WHERE user_id = ? AND key = ?', (user_id, 'backup_count'))
    row = cursor.fetchone()
    max_count = int(row['value']) if row else 3
    
    # 插入新记录
    cursor.execute('''
        INSERT INTO backups (user_id, filename, data_hash) VALUES (?, ?, ?)
    ''', (user_id, filename, data_hash))
    
    # 获取所有备份（按时间倒序）
    cursor.execute('''
        SELECT id, filename FROM backups 
        WHERE user_id = ? 
        ORDER BY backup_date DESC
    ''', (user_id,))
    backups = cursor.fetchall()
    
    # 删除超出数量的旧备份
    old_files = []
    if len(backups) > max_count:
        for backup in backups[max_count:]:
            old_files.append(backup['filename'])
            cursor.execute('DELETE FROM backups WHERE id = ?', (backup['id'],))
    
    conn.commit()
    conn.close()
    return old_files


def get_backup_list(user_id):
    """
    获取指定用户的备份列表
    
    参数:
        user_id: 用户ID
    
    返回:
        list: 备份记录列表
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT * FROM backups 
        WHERE user_id = ? 
        ORDER BY backup_date DESC
    ''', (user_id,))
    rows = cursor.fetchall()
    conn.close()
    return [dict_from_row(row) for row in rows]


def get_data_hash(user_id):
    """
    计算用户数据的哈希值
    
    参数:
        user_id: 用户ID
    
    返回:
        str: MD5哈希值
    """
    records = get_work_records(user_id, limit=100000)
    data_str = json.dumps(records, sort_keys=True)
    return hashlib.md5(data_str.encode()).hexdigest()


# ============================================================================
# 云盘配置管理（需要user_id）
# ============================================================================

def save_cloud_config(user_id, provider, access_token=None, refresh_token=None, 
                      expires_at=None, config_data=None):
    """
    保存云盘配置
    
    参数:
        user_id: 用户ID
        provider: 云盘提供商
        access_token: 访问令牌
        refresh_token: 刷新令牌
        expires_at: 过期时间
        config_data: 其他配置数据（JSON字符串）
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        INSERT OR REPLACE INTO cloud_config 
        (user_id, provider, access_token, refresh_token, expires_at, config_data, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
    ''', (user_id, provider, access_token, refresh_token, expires_at, config_data))
    conn.commit()
    conn.close()


def get_cloud_config(user_id, provider):
    """
    获取云盘配置
    
    参数:
        user_id: 用户ID
        provider: 云盘提供商
    
    返回:
        dict: 配置信息
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT * FROM cloud_config WHERE user_id = ? AND provider = ?
    ''', (user_id, provider))
    row = cursor.fetchone()
    conn.close()
    return dict_from_row(row)


def delete_cloud_config(user_id, provider):
    """
    删除云盘配置
    
    参数:
        user_id: 用户ID
        provider: 云盘提供商
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('DELETE FROM cloud_config WHERE user_id = ? AND provider = ?', (user_id, provider))
    conn.commit()
    conn.close()


# ============================================================================
# 日历视图（v1.13.0新增）
# ============================================================================

def get_calendar_data(user_id, year, month):
    """
    获取指定月份的日历数据
    
    参数:
        user_id: 用户ID
        year: 年份
        month: 月份（1-12）
    
    返回:
        dict: 包含日期标记和每日记录的字典
    """
    conn = get_db()
    cursor = conn.cursor()
    
    # 获取该月所有记录
    month_str = f"{year}-{month:02d}"
    cursor.execute('''
        SELECT work_date, record_type, location, hours 
        FROM work_records 
        WHERE user_id = ? AND deleted_at IS NULL AND work_date LIKE ?
        ORDER BY work_date
    ''', (user_id, f"{month_str}%"))
    
    rows = cursor.fetchall()
    conn.close()
    
    # 按日期分组
    date_records = {}
    for row in rows:
        date_str = row['work_date']
        if date_str not in date_records:
            date_records[date_str] = []
        date_records[date_str].append({
            'record_type': row['record_type'],
            'location': row['location'],
            'hours': row['hours']
        })
    
    # 统计每天的工时和类型
    calendar_data = {}
    for date_str, records in date_records.items():
        total_hours = sum(r['hours'] or 0 for r in records)
        # 确定主要类型（优先显示加班>手动>标准）
        types = [r['record_type'] for r in records]
        if 'overtime' in types:
            main_type = 'overtime'
        elif 'manual' in types:
            main_type = 'manual'
        else:
            main_type = 'standard'
        
        calendar_data[date_str] = {
            'type': main_type,
            'hours': round(total_hours, 1),
            'count': len(records)
        }
    
    return calendar_data


def get_yearly_report(user_id, year):
    """
    获取年度工作报告
    
    参数:
        user_id: 用户ID
        year: 年份
    
    返回:
        dict: 年度统计数据
    """
    settings = get_all_settings(user_id)
    daily_hours = float(settings.get('daily_hours', 8))
    overtime_rate = float(settings.get('overtime_rate', 8))
    meal_subsidy = float(settings.get('meal_subsidy', 15))
    daily_wage = float(settings.get('daily_wage', 0))
    
    # 获取全年记录
    records = get_records_by_year(user_id, year)
    
    # 基本统计
    total_standard = 0
    total_overtime_hours = 0
    total_manual_hours = 0
    work_days = set()  # 工作天数
    location_count = {}  # 地点统计
    
    # 月度统计
    monthly_data = {m: {'standard': 0, 'overtime': 0, 'manual': 0, 'total': 0, 'days': 0} 
                    for m in range(1, 13)}
    
    for record in records:
        hours = record.get('hours') or 0
        month = int(record['work_date'].split('-')[1])
        location = record.get('location', '未知地点')
        work_date = record['work_date']
        
        work_days.add(work_date)
        
        # 统计地点
        location_count[location] = location_count.get(location, 0) + 1
        
        # 按类型统计
        if record['record_type'] == 'standard':
            total_standard += 1
            monthly_data[month]['standard'] += 1
        elif record['record_type'] == 'overtime':
            total_overtime_hours += hours
            monthly_data[month]['overtime'] += hours
            total_standard += hours / overtime_rate if overtime_rate > 0 else 0
        elif record['record_type'] == 'manual':
            total_manual_hours += hours
            monthly_data[month]['manual'] += hours
            total_standard += hours / daily_hours if daily_hours > 0 else 0
        
        monthly_data[month]['total'] += hours / overtime_rate if record['record_type'] == 'overtime' else (hours / daily_hours if record['record_type'] == 'manual' else 1)
        monthly_data[month]['days'] += 1
    
    # TOP3地点
    top_locations = sorted(location_count.items(), key=lambda x: x[1], reverse=True)[:3]
    
    # 平均每日工时
    avg_daily_hours = total_standard / len(work_days) if work_days else 0
    
    # 计算去年数据（用于对比）
    last_year_records = get_records_by_year(user_id, year - 1)
    last_year_hours = 0
    for record in last_year_records:
        hours = record.get('hours') or 0
        if record['record_type'] == 'overtime':
            last_year_hours += hours / overtime_rate
        elif record['record_type'] == 'manual':
            last_year_hours += hours / daily_hours
        else:
            last_year_hours += 1
    
    # 同比增长
    yoy_change = 0
    if last_year_hours > 0:
        yoy_change = round(((total_standard - last_year_hours) / last_year_hours) * 100, 1)
    
    return {
        'year': year,
        'total_standard': round(total_standard, 2),
        'total_work_days': len(work_days),
        'avg_daily_hours': round(avg_daily_hours, 2),
        'total_overtime_hours': round(total_overtime_hours, 2),
        'total_meal_subsidy': round((len([r for r in records if r['record_type'] == 'standard']) * meal_subsidy) + (total_manual_hours / daily_hours * meal_subsidy if daily_hours > 0 else 0), 2),
        'total_wage': round(total_standard * daily_wage, 2),
        'top_locations': [{'location': loc, 'count': count} for loc, count in top_locations],
        'monthly_data': {
            'labels': [f"{year}-{m:02d}" for m in range(1, 13)],
            'values': [round(monthly_data[m]['total'], 2) for m in range(1, 13)]
        },
        'last_year_hours': round(last_year_hours, 2),
        'yoy_change': yoy_change
    }


# ============================================================================
# 数据导入（v1.13.0新增）
# ============================================================================

def import_work_records(user_id, records_data):
    """
    批量导入记工记录
    
    参数:
        user_id: 用户ID
        records_data: 记录数据列表
    
    返回:
        dict: 导入结果统计
    """
    conn = get_db()
    cursor = conn.cursor()
    
    success_count = 0
    error_count = 0
    errors = []
    
    for idx, record in enumerate(records_data):
        try:
            record_type = record.get('record_type', 'manual')
            work_date = record.get('work_date')
            location = record.get('location', '')
            start_time = record.get('start_time')
            end_time = record.get('end_time')
            hours = record.get('hours', 8)
            
            if not work_date or not location:
                error_count += 1
                errors.append(f"第{idx + 1}行：缺少必填字段")
                continue
            
            cursor.execute('''
                INSERT INTO work_records (user_id, record_type, work_date, location, 
                                        start_time, end_time, hours)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            ''', (user_id, record_type, work_date, location, start_time, end_time, hours))
            success_count += 1
            
        except Exception as e:
            error_count += 1
            errors.append(f"第{idx + 1}行：{str(e)}")
    
    conn.commit()
    conn.close()
    
    return {
        'success_count': success_count,
        'error_count': error_count,
        'errors': errors[:10]  # 最多返回10条错误
    }


def get_missed_days(user_id, days=7):
    """
    获取漏记的日期（最近N天）
    
    参数:
        user_id: 用户ID
        days: 检查天数，默认7
    
    返回:
        list: 漏记的日期列表
    """
    settings = get_all_settings(user_id)
    exclude_weekend = settings.get('reminder_exclude_weekend', '1') == '1'
    
    conn = get_db()
    cursor = conn.cursor()
    
    missed_days = []
    today = datetime.now().date()
    
    for i in range(1, days + 1):
        check_date = today - timedelta(days=i)
        date_str = check_date.strftime('%Y-%m-%d')
        
        # 检查是否是周末
        if exclude_weekend and check_date.weekday() >= 5:
            continue
        
        # 检查是否有记录
        cursor.execute('''
            SELECT COUNT(*) as cnt FROM work_records 
            WHERE user_id = ? AND work_date = ? AND deleted_at IS NULL
        ''', (user_id, date_str))
        
        row = cursor.fetchone()
        if row['cnt'] == 0:
            missed_days.append({
                'date': date_str,
                'weekday': ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][check_date.weekday()]
            })
    
    conn.close()
    return missed_days


def validate_hours(record_type, hours):
    """
    校验工时是否异常
    
    参数:
        record_type: 记录类型
        hours: 工时
    
    返回:
        dict: 校验结果
    """
    warnings = []
    
    if record_type in ['standard', 'manual']:
        if hours > 12:
            warnings.append({
                'type': 'too_high',
                'message': f'今日工时{hours}小时超过12小时，确认是否正确？'
            })
        elif hours > 0 and hours < 1:
            warnings.append({
                'type': 'too_low',
                'message': f'今日工时{hours}小时不足1小时，确认是否正确？'
            })
    
    return {
        'has_warning': len(warnings) > 0,
        'warnings': warnings
    }


def get_favorite_locations(user_id, limit=5):
    """
    获取常用工作地点（基于历史记录频次）
    
    参数:
        user_id: 用户ID
        limit: 返回数量，默认5
    
    返回:
        list: 常用地点列表
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute('''
        SELECT location, COUNT(*) as count 
        FROM work_records 
        WHERE user_id = ? AND deleted_at IS NULL
        GROUP BY location 
        ORDER BY count DESC 
        LIMIT ?
    ''', (user_id, limit))
    
    rows = cursor.fetchall()
    conn.close()
    
    return [{'location': row['location'], 'count': row['count']} for row in rows]


import json
