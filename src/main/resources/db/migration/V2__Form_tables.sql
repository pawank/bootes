
create table form_elements (
                      id serial primary key,
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
                      updated_by varchar(255)
);
create index idx_name_form_elements on form_elements(name);
create index idx_title_form_elements on form_elements(title);

create table options (
                      id serial primary key,
                      "value" varchar(255) not null,
                      text varchar(255) not null,
                      element_id integer not null,
                      constraint fk_element_id foreign key(element_id) references form_elements(id)
);

create table validations (
                      id serial primary key,
                      type varchar(255) not null,
                      title varchar(255) not null,
                      minimum int,
                      maximum int,
                      "values" varchar[],
                      element_id integer not null,
                      constraint fk_form_element_id foreign key(element_id) references form_elements(id)
);
create unique index idx_validations_title on validations (title);
