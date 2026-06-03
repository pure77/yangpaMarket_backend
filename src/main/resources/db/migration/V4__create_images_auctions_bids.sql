-- V4: 경매 CRUD를 위한 images / auctions / auction_images / bids 테이블 생성.
-- db/mysql/init/001_schema.sql의 권위 설계를 그대로 이식(타입/제약/인덱스 보존).
-- bids는 auctions.highest_bid_id FK 충족용으로 테이블만 생성(입찰 API는 이번 범위 외).

CREATE TABLE images (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id CHAR(26) NOT NULL,
  uploader_user_id BIGINT UNSIGNED NOT NULL,
  storage_provider ENUM('S3', 'LOCAL') NOT NULL DEFAULT 'S3',
  object_key VARCHAR(500) NOT NULL,
  file_url VARCHAR(1000) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size_bytes BIGINT UNSIGNED NOT NULL,
  width INT UNSIGNED DEFAULT NULL,
  height INT UNSIGNED DEFAULT NULL,
  upload_status ENUM('UPLOADED', 'ATTACHED', 'DELETED') NOT NULL DEFAULT 'UPLOADED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  attached_at DATETIME(3) DEFAULT NULL,
  deleted_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_images_public_id (public_id),
  UNIQUE KEY uk_images_object_key (object_key),
  KEY idx_images_uploader_status_created_at (uploader_user_id, upload_status, created_at),
  CONSTRAINT fk_images_uploader
    FOREIGN KEY (uploader_user_id) REFERENCES users (id)
    ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE auctions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id CHAR(26) NOT NULL,
  seller_user_id BIGINT UNSIGNED NOT NULL,
  winner_user_id BIGINT UNSIGNED DEFAULT NULL,
  highest_bid_id BIGINT UNSIGNED DEFAULT NULL,
  primary_image_id BIGINT UNSIGNED DEFAULT NULL,
  title VARCHAR(120) NOT NULL,
  description TEXT NOT NULL,
  category ENUM('ELECTRONICS', 'FASHION', 'HOME_APPLIANCE', 'COLLECTIBLE', 'SPORTS', 'ETC') NOT NULL,
  item_condition ENUM('UNUSED', 'LIKE_NEW', 'USED') NOT NULL,
  status ENUM('ACTIVE', 'PAYMENT_PENDING', 'PAID', 'ENDED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  start_price BIGINT UNSIGNED NOT NULL,
  current_price BIGINT UNSIGNED NOT NULL,
  minimum_bid_increment BIGINT UNSIGNED NOT NULL DEFAULT 10000,
  buy_now_price BIGINT UNSIGNED DEFAULT NULL,
  bid_count INT UNSIGNED NOT NULL DEFAULT 0,
  start_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  end_at DATETIME(3) NOT NULL,
  payment_pending_expires_at DATETIME(3) DEFAULT NULL,
  ended_at DATETIME(3) DEFAULT NULL,
  paid_at DATETIME(3) DEFAULT NULL,
  cancelled_at DATETIME(3) DEFAULT NULL,
  cancel_reason VARCHAR(255) DEFAULT NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_auctions_public_id (public_id),
  KEY idx_auctions_status_end_at (status, end_at),
  KEY idx_auctions_seller_status_created_at (seller_user_id, status, created_at),
  KEY idx_auctions_category_status_end_at (category, status, end_at),
  KEY idx_auctions_winner_status (winner_user_id, status),
  KEY fk_auctions_primary_image (primary_image_id),
  KEY fk_auctions_highest_bid (highest_bid_id),
  FULLTEXT KEY ftx_auctions_title_description (title, description),
  CONSTRAINT chk_auctions_price_floor CHECK (current_price >= start_price),
  CONSTRAINT chk_auctions_minimum_increment CHECK (minimum_bid_increment > 0),
  CONSTRAINT chk_auctions_buy_now CHECK (buy_now_price IS NULL OR buy_now_price > start_price),
  CONSTRAINT fk_auctions_seller
    FOREIGN KEY (seller_user_id) REFERENCES users (id)
    ON DELETE RESTRICT,
  CONSTRAINT fk_auctions_winner
    FOREIGN KEY (winner_user_id) REFERENCES users (id)
    ON DELETE RESTRICT,
  CONSTRAINT fk_auctions_primary_image
    FOREIGN KEY (primary_image_id) REFERENCES images (id)
    ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE auction_images (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  auction_id BIGINT UNSIGNED NOT NULL,
  image_id BIGINT UNSIGNED NOT NULL,
  sort_order SMALLINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_auction_images_auction_image (auction_id, image_id),
  UNIQUE KEY uk_auction_images_auction_sort_order (auction_id, sort_order),
  KEY idx_auction_images_image_id (image_id),
  CONSTRAINT fk_auction_images_auction
    FOREIGN KEY (auction_id) REFERENCES auctions (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_auction_images_image
    FOREIGN KEY (image_id) REFERENCES images (id)
    ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE bids (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id CHAR(26) NOT NULL,
  auction_id BIGINT UNSIGNED NOT NULL,
  bidder_user_id BIGINT UNSIGNED NOT NULL,
  amount BIGINT UNSIGNED NOT NULL,
  is_winning_bid TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bids_public_id (public_id),
  KEY idx_bids_auction_created_at (auction_id, created_at DESC),
  KEY idx_bids_auction_amount_created_at (auction_id, amount DESC, created_at DESC),
  KEY idx_bids_bidder_created_at (bidder_user_id, created_at DESC),
  CONSTRAINT chk_bids_amount_positive CHECK (amount > 0),
  CONSTRAINT fk_bids_auction
    FOREIGN KEY (auction_id) REFERENCES auctions (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_bids_bidder
    FOREIGN KEY (bidder_user_id) REFERENCES users (id)
    ON DELETE RESTRICT
) ENGINE=InnoDB;

-- bids 생성 후 순환 참조 해소: auctions.highest_bid_id → bids
ALTER TABLE auctions
  ADD CONSTRAINT fk_auctions_highest_bid
    FOREIGN KEY (highest_bid_id) REFERENCES bids (id)
    ON DELETE SET NULL;
