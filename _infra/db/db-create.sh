POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_SRV=db.jobico.org
export PGPASSWORD=postgres
APP_DB_PASS=todo
APP_DB_USER=todo
APP_DB_NAME=todo

psql -h "$POSTGRES_SRV" -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE USER $APP_DB_USER WITH PASSWORD '$APP_DB_PASS';
  CREATE DATABASE $APP_DB_NAME;

  GRANT ALL PRIVILEGES ON DATABASE $APP_DB_NAME TO $APP_DB_USER;
  
  \connect $APP_DB_NAME $POSTGRES_USER
  
  GRANT ALL ON SCHEMA public TO $APP_DB_USER;
EOSQL

export PGPASSWORD=$APP_DB_PASS

psql -h "$POSTGRES_SRV" -v ON_ERROR_STOP=1 --username "$APP_DB_USER" --dbname "$APP_DB_NAME" <<-EOSQL
  BEGIN;
    /*
    Drops
    */

    DROP TABLE IF EXISTS MUSER CASCADE;
    DROP TABLE IF EXISTS LABEL_ACTIVITY CASCADE;
    DROP TABLE IF EXISTS LABEL CASCADE;
    DROP TABLE IF EXISTS MEDIA CASCADE;
    DROP TABLE IF EXISTS ACTIVITY CASCADE;
    DROP FUNCTION IF EXISTS trigger_set_timestamp;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

    /* 
    Functions
    */
    CREATE OR REPLACE FUNCTION trigger_set_timestamp()
    RETURNS TRIGGER AS \$\$ BEGIN NEW.updated_at = NOW();
    RETURN NEW;
    END
    \$\$ language 'plpgsql';

    /*  
    MUSER 
    */
    CREATE TABLE IF
    NOT EXISTS MUSER(
        id bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,

          public_id uuid UNIQUE NOT NULL DEFAULT uuid_generate_v4(),
        created_at
    timestamptz NOT NULL DEFAULT NOW(),
        updated_at timestamptz NOT NULL DEFAULT
    NOW(),
        full_name text,
        email text
    );
    CREATE OR REPLACE
    TRIGGER set_timestamp_MUSER BEFORE
    UPDATE ON MUSER FOR EACH ROW EXECUTE PROCEDURE
    trigger_set_timestamp();

    /* 
    ACTIVITY
    */
    CREATE TABLE IF
    NOT EXISTS ACTIVITY(
        id bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,

          public_id uuid UNIQUE NOT NULL DEFAULT uuid_generate_v4(),
        created_at
    timestamptz NOT NULL DEFAULT NOW(),
        updated_at timestamptz NOT NULL DEFAULT
    NOW(),
        name text,
        description text,
        TYPE text NOT NULL,
        title text,
        summary text,
        state text NOT NULL,
        STATUS text NOT
    NULL,
        tags text [],
        extra_data JSONB,
        muser_id bigint
    NOT NULL,
        CONSTRAINT fk_muser FOREIGN KEY(muser_id) REFERENCES muser(id)
    ON DELETE CASCADE
    );

    CREATE OR REPLACE TRIGGER set_timestamp_ACTIVITY
    BEFORE
    UPDATE ON ACTIVITY FOR EACH ROW EXECUTE PROCEDURE trigger_set_timestamp();



      /* 
    LABEL 
    */
    CREATE TABLE IF NOT EXISTS LABEL(
        id
    bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
        public_id uuid UNIQUE
    NOT NULL DEFAULT uuid_generate_v4(),
        created_at timestamptz NOT NULL
    DEFAULT NOW(),
        updated_at timestamptz NOT NULL DEFAULT NOW(),
        name
    text,
        description text,
        muser_id bigint NOT NULL,
        CONSTRAINT
    fk_muser FOREIGN KEY(muser_id) REFERENCES muser(id) ON DELETE CASCADE
    );


      CREATE OR REPLACE TRIGGER set_timestamp_LABEL BEFORE
    UPDATE ON LABEL
    FOR EACH ROW EXECUTE PROCEDURE trigger_set_timestamp();

    /*
    MEDIA

      */
    CREATE TABLE IF NOT EXISTS MEDIA(
        id bigint PRIMARY KEY GENERATED
    BY DEFAULT AS IDENTITY,
        public_id uuid UNIQUE NOT NULL DEFAULT uuid_generate_v4(),

          created_at timestamptz NOT NULL DEFAULT NOW(),
        updated_at timestamptz
    NOT NULL DEFAULT NOW(),
        name text,
        description text,
        TYPE
    text NOT NULL,
        URI text,
        extra_data JSON,
        activity_id
    bigint,
        CONSTRAINT fk_activity FOREIGN KEY(activity_id) REFERENCES activity(id)
    ON DELETE CASCADE
    );
    CREATE OR REPLACE TRIGGER set_timestamp_MEDIA BEFORE

      UPDATE ON MEDIA FOR EACH ROW EXECUTE PROCEDURE trigger_set_timestamp();


      /* 
    LABEL_ACTIVITY
    */
    CREATE TABLE LABEL_ACTIVITY(
        id
    bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
        label_id bigint,

          activity_id bigint,
        created_at timestamptz NOT NULL DEFAULT NOW(),

          updated_at timestamptz NOT NULL DEFAULT NOW(),
        CONSTRAINT fk_label
    FOREIGN KEY(label_id) REFERENCES label(id) ON DELETE CASCADE,
        CONSTRAINT
    fk_activity FOREIGN KEY(activity_id) REFERENCES activity(id) ON DELETE CASCADE

      );
    CREATE OR REPLACE TRIGGER set_timestamp_LABEL_ACTIVITY BEFORE
    UPDATE
    ON LABEL_ACTIVITY FOR EACH ROW EXECUTE PROCEDURE trigger_set_timestamp();


      /*
    Alters
    */
    ALTER TABLE ACTIVITY
    ADD COLUMN label_activity_id
    bigint REFERENCES LABEL_ACTIVITY(id) ON DELETE
    SET NULL;

    /*
    Test
    data
    */
    DO \$\$
    DECLARE v_muser_id bigint;
    DECLARE v_activity_id
    bigint;
    DECLARE v_label_id bigint;
    DECLARE v_label_activity_id bigint;

      BEGIN
    /*
    User 1
    */
    INSERT INTO MUSER (full_name, email,
    public_id)
    VALUES ('User_test_1', 'User@test1.com', '672d2e23-b824-41ca-97b0-6efe2d589842')

      RETURNING id INTO v_muser_id; 

    INSERT INTO ACTIVITY (
            name,

              description,
            TYPE,
            state,
            STATUS,

              tags,
            extra_data,
            muser_id
        )

      VALUES(
            'Activity1',
            'My activity 1',
            'DOC',

              'active',
            'completed',
            '{ "tag1",
            "tag2"
    }',
            '{ "attr1" :"Attr1_v",
            "attr2" :"Attr2_v"
    }',
            v_muser_id
        )
    RETURNING id INTO v_activity_id; 


      INSERT INTO MEDIA (
            name,
            description,
            TYPE,

              URI,
            extra_data,
            activity_id
        )

      VALUES (
            'PICTURE_URL',
            'URL1',
            'IMAGE',

              'http://media.com/p.jpg',
            '{"width":100}',
            v_activity_id

          );

    INSERT INTO MEDIA (
            name,
            description,

              TYPE,
            URI,
            extra_data,
            activity_id

          )
    VALUES (
            'PICTURE_URL2',
            'URL2',
            'IMAGE',

              'http://via.com/k.jpg',
            '{"height":100}',
            v_activity_id

          ); 

    INSERT INTO LABEL (name, description, public_id, muser_id)

      VALUES ('Label1', 'label 1', '53398f2e-8c80-4515-8c92-7587b75ee5d6', v_muser_id)

      RETURNING id INTO v_label_id;

    INSERT INTO LABEL_ACTIVITY (label_id,
    activity_id)
    VALUES (v_label_id, v_activity_id)
    RETURNING id INTO v_label_activity_id;


      UPDATE ACTIVITY
    SET label_activity_id = v_label_activity_id
    WHERE
    id = v_activity_id;

END \$\$;

COMMIT;

EOSQL
