create table form (
                      id uuid DEFAULT uuid_generate_v4() primary key,
                      tenant_id int not null,
                      request_id uuid,
                      template_id uuid,
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
                      updated_by varchar(255),
                      constraint fk_form_template_ref_id foreign key(template_id) references form(id)
);
create index idx_form_tenant_id_title on form(tenant_id, title);
create index idx_form_title on form(title);

create table "section" (
                      id uuid DEFAULT uuid_generate_v4() primary key,
                      form_id uuid not null,
                      constraint fk_section_form_id foreign key(form_id) references form(id)
);

create table form_element (
                      id uuid DEFAULT uuid_generate_v4() primary key,
                      seq_no integer not null,
                      name varchar(255) not null,
                      title varchar(255) not null,
                      description varchar(500),
                      "values" varchar[] not null,
                      type varchar(255) not null,
                      required boolean not null default true,
                      customer_error varchar(255),
                      errors varchar[],
                      options_type varchar(255),
                      delay_in_seconds int,
                      show_progress_bar boolean default false,
                      progress_bar_uri varchar(500),
                      "action" boolean,
                      status varchar(255),
                      created_at timestamp not null,
                      updated_at timestamp,
                      created_by varchar(255) not null,
                      updated_by varchar(255),
                      form_id uuid not null,
                      section_name varchar(255) not null,
                      section_seq_no integer not null,
                      constraint fk_form_elements_form_id foreign key(form_id) references form(id)
);
create index idx_name_form_element on form_element(name);
create index idx_title_form_element on form_element(title);

create table options (
                      id uuid DEFAULT uuid_generate_v4() primary key,
                      "value" varchar(255) not null,
                      text varchar(255) not null,
                      seq_no integer,
                      element_id uuid not null,
                      constraint fk_element_id foreign key(element_id) references form_element(id)
);

create table validations (
                      id uuid DEFAULT uuid_generate_v4() primary key,
                      type varchar(255) not null,
                      title varchar(255) not null,
                      minimum int,
                      maximum int,
                      "values" varchar[],
                      element_id uuid not null,
                      constraint fk_form_element_id foreign key(element_id) references form_element(id)
);
create index idx_validations_title on validations (title);
