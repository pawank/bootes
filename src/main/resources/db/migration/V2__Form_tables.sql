create table form (
                      id serial primary key,
                      uid varchar(255) not null,
                      title varchar(255) not null,
                      sub_title varchar(255),
                      width int,
                      height int,
                      background_color varchar(50),
                      text_color varchar(50),
                      font_family varchar(50),
                      status varchar(255),
                      created_at timestamp not null,
                      updated_at timestamp,
                      created_by varchar(255) not null,
                      updated_by varchar(255)
);
create index idx_uid_form on form(uid);
create index idx_title_form on form(title);

create table "section" (
                      id serial primary key,
                      form_id integer not null,
                      constraint fk_section_form_id foreign key(form_id) references form(id)
);

create table form_element (
                      id serial primary key,
                      seq_no integer not null,
                      name varchar(255) not null,
                      title varchar(255) not null,
                      description varchar(500),
                      "values" varchar[] not null,
                      type varchar(255) not null,
                      required boolean not null default true,
                      custom_error varchar(255),
                      errors varchar[],
                      option_type varchar(255),
                      delay_in_seconds int,
                      show_progress_bar boolean default false,
                      progress_bar_uri varchar(500),
                      "action" boolean,
                      status varchar(255),
                      created_at timestamp not null,
                      updated_at timestamp,
                      created_by varchar(255) not null,
                      updated_by varchar(255),
                      section_id integer,
                      form_id integer not null,
                      section_name varchar(255) not null,
                      constraint fk_form_section_id foreign key(section_id) references "section"(id),
                      constraint fk_form_elements_form_id foreign key(form_id) references form(id)
);
create index idx_name_form_element on form_element(name);
create index idx_title_form_element on form_element(title);

create table options (
                      id serial primary key,
                      "value" varchar(255) not null,
                      text varchar(255) not null,
                      element_id integer not null,
                      constraint fk_element_id foreign key(element_id) references form_element(id)
);

create table validations (
                      id serial primary key,
                      type varchar(255) not null,
                      title varchar(255) not null,
                      minimum int,
                      maximum int,
                      "values" varchar[],
                      element_id integer not null,
                      constraint fk_form_element_id foreign key(element_id) references form_element(id)
);
create unique index idx_validations_title on validations (title);
